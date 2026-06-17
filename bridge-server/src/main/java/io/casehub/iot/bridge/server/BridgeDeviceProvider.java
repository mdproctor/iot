package io.casehub.iot.bridge.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.iot.api.CommandResult;
import io.casehub.iot.api.DeviceCommand;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.ProviderStatus;
import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.api.bridge.BridgeMessage;
import io.casehub.iot.api.bridge.DeviceIdUtils;
import io.casehub.iot.api.spi.DeviceProvider;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.UniEmitter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Server-side {@link DeviceProvider} that presents remote bridge-agent devices as local.
 *
 * <p>Device snapshots arrive from bridge agents via WebSocket. Each snapshot replaces the
 * entire device set for a tenancy; differences are computed and returned as
 * {@link StateChangeEvent}s for the event pipeline to fire.
 *
 * <p>All incoming device IDs are namespaced with the tenancy prefix
 * ({@code tenancyId/localId}) to prevent collisions across sites.
 */
@ApplicationScoped
public class BridgeDeviceProvider implements DeviceProvider {

    private static final Logger LOG = Logger.getLogger(BridgeDeviceProvider.class);
    private static final String PROVIDER_ID = "bridge";

    private final DeviceIdNamespacer namespacer;
    private final BridgeConnectionRegistry registry;
    private final ObjectMapper mapper;
    private final BridgeServerConfig config;

    /**
     * Per-tenancy device maps. Outer key = tenancyId, inner key = namespaced deviceId.
     */
    private final ConcurrentHashMap<String, Map<String, DeviceEntity>> tenancyDevices =
            new ConcurrentHashMap<>();

    private final ConcurrentHashMap<String, UniEmitter<? super CommandResult>> pendingCommands =
            new ConcurrentHashMap<>();

    @Inject
    public BridgeDeviceProvider(DeviceIdNamespacer namespacer, BridgeConnectionRegistry registry,
                                ObjectMapper mapper, BridgeServerConfig config) {
        this.namespacer = namespacer;
        this.registry = registry;
        this.mapper = mapper;
        this.config = config;
    }

    public void completeCommand(String tenancyId, String correlationId, CommandResult result) {
        String compositeKey = tenancyId + "/" + correlationId;
        UniEmitter<? super CommandResult> emitter = pendingCommands.remove(compositeKey);
        if (emitter != null) {
            emitter.complete(result);
        }
    }

    @Override
    public String providerId() {
        return PROVIDER_ID;
    }

    @Override
    public Uni<List<DeviceEntity>> discover() {
        return Uni.createFrom().item(() -> {
            List<DeviceEntity> all = new ArrayList<>();
            for (Map<String, DeviceEntity> devices : tenancyDevices.values()) {
                all.addAll(devices.values());
            }
            return List.copyOf(all);
        });
    }

    @Override
    public Uni<CommandResult> dispatch(DeviceCommand command) {
        String tenancyId = DeviceIdUtils.extractTenancyId(command.targetDeviceId());
        if (registry.getSession(tenancyId).isEmpty()) {
            return Uni.createFrom().item(CommandResult.FAILED);
        }

        String correlationId = command.correlationId() != null
                ? command.correlationId() : UUID.randomUUID().toString();
        String localId = DeviceIdUtils.stripPrefix(command.targetDeviceId());
        DeviceCommand localCommand = new DeviceCommand(
                localId, command.action(), command.parameters(),
                command.dispatchedBy(), correlationId);
        BridgeMessage.Command bridgeCommand = new BridgeMessage.Command(
                tenancyId, Instant.now(), correlationId, localCommand);

        String json;
        try {
            json = mapper.writeValueAsString(bridgeCommand);
        } catch (JsonProcessingException e) {
            LOG.errorf(e, "Failed to serialize command [correlationId=%s]", correlationId);
            return Uni.createFrom().item(CommandResult.FAILED);
        }

        var connection = registry.getSession(tenancyId).orElseThrow();
        String compositeKey = tenancyId + "/" + correlationId;

        return Uni.createFrom().<CommandResult>emitter(emitter -> {
            pendingCommands.put(compositeKey, emitter);
            connection.sendText(json).subscribe().with(
                    success -> { },
                    failure -> {
                        pendingCommands.remove(compositeKey);
                        emitter.complete(CommandResult.FAILED);
                    });
        })
        .ifNoItem().after(Duration.ofSeconds(config.commandTimeoutSeconds()))
        .recoverWithItem(CommandResult.TIMEOUT)
        .onTermination().invoke(() -> pendingCommands.remove(compositeKey));
    }

    @Override
    public ProviderStatus status() {
        if (!registry.hasAnyConnection()) {
            return ProviderStatus.DISCONNECTED;
        }
        if (registry.isFullyConnected()) {
            return ProviderStatus.CONNECTED;
        }
        return ProviderStatus.CONNECTING;
    }

    /**
     * Process a full device snapshot from a bridge agent.
     *
     * <p>Namespaces all incoming device IDs, diffs against the previous snapshot for this
     * tenancy, and returns the resulting {@link StateChangeEvent}s:
     * <ul>
     *   <li><b>New device</b> — {@code before=null}, changedCapabilities = all capabilities</li>
     *   <li><b>Changed device</b> — derived via {@link StateChangeEvent#deriveChangedCapabilities}</li>
     *   <li><b>Removed device</b> — marked unavailable, changedCapabilities = {"available"}</li>
     * </ul>
     *
     * @return events to fire (caller is responsible for CDI event firing)
     */
    public List<StateChangeEvent> onSnapshot(String tenancyId, List<DeviceEntity> incoming) {
        Instant now = Instant.now();
        List<StateChangeEvent> events = new ArrayList<>();

        // Namespace all incoming devices
        Map<String, DeviceEntity> namespacedIncoming = new ConcurrentHashMap<>();
        for (DeviceEntity device : incoming) {
            DeviceEntity namespaced = namespacer.namespace(device, tenancyId);
            namespacedIncoming.put(namespaced.deviceId(), namespaced);
        }

        Map<String, DeviceEntity> previous = tenancyDevices.getOrDefault(tenancyId, Map.of());

        // Detect new and changed devices
        for (var entry : namespacedIncoming.entrySet()) {
            String deviceId = entry.getKey();
            DeviceEntity after = entry.getValue();
            DeviceEntity before = previous.get(deviceId);

            if (before == null) {
                // New device — all capabilities are "changed"
                events.add(new StateChangeEvent(
                        null, after, after.capabilities().keySet(), now, PROVIDER_ID));
            } else {
                Set<String> changed = StateChangeEvent.deriveChangedCapabilities(before, after);
                if (!changed.isEmpty()) {
                    events.add(new StateChangeEvent(before, after, changed, now, PROVIDER_ID));
                }
            }
        }

        // Detect removed devices (present before, absent now)
        for (var entry : previous.entrySet()) {
            if (!namespacedIncoming.containsKey(entry.getKey())) {
                DeviceEntity removed = namespacer.markUnavailable(entry.getValue());
                events.add(new StateChangeEvent(
                        entry.getValue(), removed, Set.of(DeviceEntity.CAP_AVAILABLE), now, PROVIDER_ID));
            }
        }

        // Replace device map for this tenancy
        tenancyDevices.put(tenancyId, namespacedIncoming);

        return events;
    }

    /**
     * Process a single state-change event from a bridge agent (live pass-through).
     *
     * <p>Namespaces the event's {@code after} device and updates the device map.
     */
    public StateChangeEvent onStateChange(StateChangeEvent event, String tenancyId) {
        DeviceEntity namespacedAfter = namespacer.namespace(event.after(), tenancyId);
        DeviceEntity namespacedBefore = event.before() != null
                ? namespacer.namespace(event.before(), tenancyId) : null;
        tenancyDevices.computeIfAbsent(tenancyId, k -> new ConcurrentHashMap<>())
                .put(namespacedAfter.deviceId(), namespacedAfter);
        return new StateChangeEvent(namespacedBefore, namespacedAfter,
                event.changedCapabilities(), event.occurredAt(), event.providerId());
    }
}

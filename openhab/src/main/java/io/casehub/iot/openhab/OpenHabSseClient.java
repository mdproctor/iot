package io.casehub.iot.openhab;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.iot.api.DeviceCommand;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.ProviderStatus;
import io.casehub.iot.api.ProviderStatusEvent;
import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.openhab.internal.OpenHabItemDto;
import io.casehub.iot.openhab.internal.OpenHabSseEventDto;
import io.casehub.iot.openhab.internal.OpenHabStatePayloadDto;
import io.smallrye.mutiny.Uni;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SSE client for OpenHAB event stream with Equipment-level state caching.
 *
 * <p>Connects to OpenHAB's SSE /rest/events endpoint and maintains a
 * device cache keyed by Equipment Group name. Individual item state
 * changes are resolved to their parent Equipment, the Equipment is
 * reconstructed with updated member states, and a coalesced
 * {@link StateChangeEvent} is fired after a configurable window
 * (default 50ms) to batch rapid item-level changes into a single
 * Equipment-level event.</p>
 *
 * <p>The client handles connection lifecycle including exponential backoff
 * reconnection with jitter, mirroring the pattern used by the Home
 * Assistant WebSocket client.</p>
 */
@ApplicationScoped
public class OpenHabSseClient {

    private static final Logger LOG = Logger.getLogger(OpenHabSseClient.class);

    @Inject @RestClient OpenHabRestClient restClient;
    @Inject @RestClient OpenHabSseRestClient sseRestClient;
    @Inject OpenHabEntityMapper mapper;
    @Inject OpenHabConfig config;
    @Inject Event<StateChangeEvent> stateEvents;
    @Inject Event<ProviderStatusEvent> statusEvents;
    @Inject ObjectMapper objectMapper;

    // ---- state caches ----

    /** Equipment Group item DTO — keyed by Equipment name. */
    private final ConcurrentHashMap<String, OpenHabItemDto> equipmentCache = new ConcurrentHashMap<>();

    /** Item states per Equipment — Equipment name -> (item name -> latest DTO with updated state). */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, OpenHabItemDto>> itemStateCache = new ConcurrentHashMap<>();

    /** Assembled DeviceEntity per Equipment — keyed by Equipment name. */
    private final ConcurrentHashMap<String, DeviceEntity> deviceCache = new ConcurrentHashMap<>();

    /** Reverse index: item name -> Equipment name. */
    private final ConcurrentHashMap<String, String> itemToEquipment = new ConcurrentHashMap<>();

    /** Coalescing "before" snapshot — Equipment name -> DeviceEntity before the batch. */
    private final ConcurrentHashMap<String, DeviceEntity> coalescingBefore = new ConcurrentHashMap<>();

    /** Coalescing pending timers — Equipment name -> scheduled future. */
    private final ConcurrentHashMap<String, ScheduledFuture<?>> coalescingTimers = new ConcurrentHashMap<>();

    // ---- connection state ----

    private volatile ProviderStatus currentStatus = ProviderStatus.DISCONNECTED;
    private volatile boolean shuttingDown = false;
    private volatile boolean firstEventReceived = false;
    private volatile io.smallrye.mutiny.subscription.Cancellable sseSubscription;
    private final AtomicInteger reconnectAttempt = new AtomicInteger(0);
    private final ScheduledExecutorService executor =
        Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "oh-sse-reconnect");
            t.setDaemon(true);
            return t;
        });

    /** CDI constructor — used by the container. */
    @Inject
    OpenHabSseClient() {
        // Injection targets set by CDI
    }

    /** Test constructor — bypasses CDI for unit testing cache and resolution logic. */
    OpenHabSseClient(OpenHabEntityMapper mapper, ObjectMapper objectMapper) {
        this.mapper = mapper;
        this.objectMapper = objectMapper;
    }

    // ---- public API ----

    /**
     * Connects to OpenHAB: discovers Equipment via REST, then subscribes to SSE events.
     *
     * @return completes when the initial REST discovery finishes; SSE subscription is fire-and-forget
     */
    public Uni<Void> connect() {
        io.smallrye.mutiny.subscription.Cancellable old = sseSubscription;
        if (old != null) {
            old.cancel();
        }
        sseSubscription = null;

        fireStatus(ProviderStatus.CONNECTING);
        firstEventReceived = false;

        return restClient.getItems("Equipment", true)
            .invoke(this::populateCaches)
            .invoke(items -> subscribeSse())
            .replaceWithVoid()
            .onFailure().invoke(e -> {
                LOG.warnf(e, "OpenHAB discovery failed");
                fireStatus(ProviderStatus.DISCONNECTED);
                scheduleReconnect();
            });
    }

    public ProviderStatus currentStatus() {
        return currentStatus;
    }

    /**
     * Returns a defensive copy of all currently cached devices.
     */
    public List<DeviceEntity> cachedDevices() {
        return List.copyOf(deviceCache.values());
    }

    /**
     * Resolves the target item name for command dispatch.
     *
     * <p>Uses the Equipment member index built during discovery to find the
     * member whose semantic tags match the command action's required Point tags.</p>
     *
     * @param command the device command to resolve
     * @return the OpenHAB item name to send the command to, or null if unresolved
     */
    public String resolveTargetItem(DeviceCommand command) {
        String equipmentName = command.targetDeviceId();
        if (!equipmentCache.containsKey(equipmentName)) {
            return null;
        }

        ConcurrentHashMap<String, OpenHabItemDto> members = itemStateCache.get(equipmentName);
        if (members == null || members.isEmpty()) {
            return null;
        }

        // Special case: set_position tries Status+OpenState first, then Control+OpenState
        if (DeviceCommand.ACTION_SET_POSITION.equals(command.action())) {
            String result = findMemberWithTags(members, Set.of("Status", "OpenState"));
            if (result != null) {
                return result;
            }
            return findMemberWithTags(members, Set.of("Control", "OpenState"));
        }

        Set<String> requiredTags = resolveRequiredTags(command);
        if (requiredTags == null) {
            return null;
        }

        return findMemberWithTags(members, requiredTags);
    }

    private String findMemberWithTags(ConcurrentHashMap<String, OpenHabItemDto> members, Set<String> requiredTags) {
        for (OpenHabItemDto member : members.values()) {
            Set<String> memberTags = member.tagSet();
            if (memberTags.containsAll(requiredTags)) {
                return member.name();
            }
        }
        return null;
    }

    // ---- cache population (package-private for testing) ----

    /**
     * Populates all caches from a discovery response.
     *
     * <p>Called on initial connect and on reconnect (re-discovers to refresh indexes).</p>
     */
    void populateCaches(List<OpenHabItemDto> equipments) {
        // Clear stale data
        equipmentCache.clear();
        itemStateCache.clear();
        deviceCache.clear();
        itemToEquipment.clear();

        for (OpenHabItemDto equipment : equipments) {
            equipmentCache.put(equipment.name(), equipment);

            ConcurrentHashMap<String, OpenHabItemDto> memberMap = new ConcurrentHashMap<>();
            if (equipment.members() != null) {
                for (OpenHabItemDto member : equipment.members()) {
                    memberMap.put(member.name(), member);
                    itemToEquipment.put(member.name(), equipment.name());
                }
            }
            itemStateCache.put(equipment.name(), memberMap);

            DeviceEntity entity = mapper.mapEquipment(equipment, Instant.now());
            if (entity != null) {
                deviceCache.put(equipment.name(), entity);
            }
        }

        LOG.infof("OpenHAB caches populated: %d equipments, %d items indexed",
            equipmentCache.size(), itemToEquipment.size());
    }

    // ---- SSE event handling (package-private for testing) ----

    /**
     * Handles a raw SSE event string.
     *
     * <p>Parses the event, resolves the item to its parent Equipment, updates the
     * item state cache, reconstructs the Equipment DTO, re-maps to DeviceEntity,
     * and triggers coalesced state change event emission.</p>
     */
    void handleSseEvent(String eventData) {
        try {
            OpenHabSseEventDto sseEvent = objectMapper.readValue(eventData, OpenHabSseEventDto.class);

            if (!"ItemStateChangedEvent".equals(sseEvent.type())) {
                return;
            }

            // Extract item name from topic: "openhab/items/{itemName}/statechanged"
            String[] topicParts = sseEvent.topic().split("/");
            if (topicParts.length < 3) {
                LOG.warnf("Unexpected SSE topic format: %s", sseEvent.topic());
                return;
            }
            String itemName = topicParts[2];

            // Look up parent Equipment
            String equipmentName = itemToEquipment.get(itemName);
            if (equipmentName == null) {
                LOG.debugf("SSE event for item %s not in any Equipment — ignoring", itemName);
                return;
            }

            // Parse payload for new state value
            OpenHabStatePayloadDto payload = objectMapper.readValue(
                sseEvent.payload(), OpenHabStatePayloadDto.class);

            // Get existing cached item DTO and create updated version
            ConcurrentHashMap<String, OpenHabItemDto> members = itemStateCache.get(equipmentName);
            if (members == null) {
                return;
            }

            OpenHabItemDto existingItem = members.get(itemName);
            if (existingItem == null) {
                return;
            }

            // Create new record with updated state
            OpenHabItemDto updatedItem = new OpenHabItemDto(
                existingItem.type(), existingItem.name(), existingItem.label(),
                payload.value(), existingItem.tags(), existingItem.members(),
                existingItem.stateDescription());

            // Store in itemStateCache
            members.put(itemName, updatedItem);

            // Reconstruct Equipment DTO with updated members
            OpenHabItemDto equip = equipmentCache.get(equipmentName);
            if (equip == null) {
                return;
            }

            OpenHabItemDto updatedEquipment = new OpenHabItemDto(
                equip.type(), equip.name(), equip.label(), equip.state(), equip.tags(),
                List.copyOf(members.values()),
                equip.stateDescription());

            // Re-map to DeviceEntity
            DeviceEntity newEntity = mapper.mapEquipment(updatedEquipment, Instant.now());
            if (newEntity == null) {
                return;
            }

            // Atomic put returns previous value — captures "before"
            DeviceEntity oldEntity = deviceCache.put(equipmentName, newEntity);

            // Coalescing: only start timer on first change in window
            coalescingTimers.computeIfAbsent(equipmentName, name -> {
                coalescingBefore.putIfAbsent(name, oldEntity);
                return scheduleCoalesced(name);
            });

        } catch (Exception e) {
            LOG.warnf(e, "Failed to process SSE event — ignoring");
        }
    }

    // ---- test accessors (package-private) ----

    String getItemToEquipment(String itemName) {
        return itemToEquipment.get(itemName);
    }

    // ---- SSE subscription ----

    private void subscribeSse() {
        sseSubscription = sseRestClient.subscribeEvents("openhab/items/*/statechanged")
            .subscribe().with(
                event -> {
                    if (!firstEventReceived) {
                        firstEventReceived = true;
                        fireStatus(ProviderStatus.CONNECTED);
                        reconnectAttempt.set(0);
                    }
                    handleSseEvent(event.data());
                },
                error -> {
                    if (!shuttingDown) {
                        LOG.warnf(error, "OpenHAB SSE stream error");
                        fireStatus(ProviderStatus.DISCONNECTED);
                        scheduleReconnect();
                    }
                },
                () -> {
                    if (!shuttingDown) {
                        LOG.info("OpenHAB SSE stream completed");
                        fireStatus(ProviderStatus.DISCONNECTED);
                        scheduleReconnect();
                    }
                }
            );
    }

    // ---- coalescing ----

    private ScheduledFuture<?> scheduleCoalesced(String equipmentName) {
        int windowMs = config != null ? config.coalesceWindowMs() : 50;
        return executor.schedule(() -> fireCoalesced(equipmentName), windowMs, TimeUnit.MILLISECONDS);
    }

    private void fireCoalesced(String equipmentName) {
        coalescingTimers.remove(equipmentName);  // Remove timer first — concurrent events now start fresh cycle
        DeviceEntity before = coalescingBefore.remove(equipmentName);
        DeviceEntity after = deviceCache.get(equipmentName);

        if (before != null && after != null) {
            try {
                Set<String> changedCapabilities = StateChangeEvent.deriveChangedCapabilities(before, after);
                if (!changedCapabilities.isEmpty()) {
                    stateEvents.fireAsync(new StateChangeEvent(
                        before, after, changedCapabilities, Instant.now(), "openhab"));
                }
            } catch (Exception e) {
                LOG.warnf(e, "Failed to fire coalesced state change for %s", equipmentName);
            }
        }
    }

    // ---- reconnection ----

    void scheduleReconnect() {
        if (shuttingDown) return;
        int attempt = reconnectAttempt.getAndIncrement();
        double base = reconnectBaseSeconds() * Math.pow(2, attempt);
        double capped = Math.min(base, reconnectMaxSeconds());
        double jittered = capped * (0.75 + 0.5 * ThreadLocalRandom.current().nextDouble());
        LOG.infof("Scheduling OpenHAB reconnect attempt %d in %.1fs", attempt + 1, jittered);
        executor.schedule(() -> connect().subscribe().with(
            v -> {},
            e -> {
                LOG.warnf(e, "OpenHAB reconnect attempt %d failed", attempt + 1);
                scheduleReconnect();
            }
        ), (long) (jittered * 1000), TimeUnit.MILLISECONDS);
    }

    private int reconnectBaseSeconds() {
        return config != null ? config.reconnectBaseSeconds() : 5;
    }

    private int reconnectMaxSeconds() {
        return config != null ? config.reconnectMaxSeconds() : 300;
    }

    // ---- status ----

    private void fireStatus(ProviderStatus newStatus) {
        ProviderStatus oldStatus = currentStatus;
        currentStatus = newStatus;
        if (statusEvents != null) {
            statusEvents.fireAsync(new ProviderStatusEvent("openhab", oldStatus, newStatus));
        }
    }

    // ---- target item resolution helpers ----

    private Set<String> resolveRequiredTags(DeviceCommand command) {
        return switch (command.action()) {
            case DeviceCommand.ACTION_TURN_ON, DeviceCommand.ACTION_TURN_OFF ->
                Set.of("Control", "Switch");
            case DeviceCommand.ACTION_SET_TEMPERATURE ->
                Set.of("Setpoint", "Temperature");
            case DeviceCommand.ACTION_LOCK, DeviceCommand.ACTION_UNLOCK ->
                Set.of("Control", "Switch");
            case DeviceCommand.ACTION_SET_POSITION ->
                null; // handled by OR logic in resolveTargetItem
            case DeviceCommand.ACTION_SET_VOLUME ->
                Set.of("Control", "SoundVolume");
            default -> {
                LOG.warnf("Unknown command action for item resolution: %s", command.action());
                yield null;
            }
        };
    }

    // ---- shutdown ----

    @PreDestroy
    public void stop() {
        shuttingDown = true;

        // Cancel SSE subscription
        io.smallrye.mutiny.subscription.Cancellable sub = sseSubscription;
        if (sub != null) {
            sub.cancel();
        }

        // Cancel all coalescing timers
        coalescingTimers.values().forEach(f -> f.cancel(false));
        coalescingTimers.clear();

        executor.shutdownNow();
    }
}

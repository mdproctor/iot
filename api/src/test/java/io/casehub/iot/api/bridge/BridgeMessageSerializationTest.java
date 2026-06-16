package io.casehub.iot.api.bridge;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.iot.api.CommandResult;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceCommand;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.ProviderStatus;
import io.casehub.iot.api.ProviderStatusEvent;
import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.api.SwitchDevice;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BridgeMessageSerializationTest {

    private static final Instant NOW = Instant.parse("2026-06-16T10:00:00Z");
    private static final String TENANCY = "tenant-1";

    private static JsonMapper mapper;

    @BeforeAll
    static void setupMapper() {
        mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
    }

    // --- Helper builders ---

    private SwitchDevice switchDevice(String id, boolean on) {
        return SwitchDevice.builder()
                .deviceId(id).deviceClass(DeviceClass.SWITCH).label("Switch " + id)
                .available(true).lastUpdated(NOW).tenancyId(TENANCY)
                .on(on)
                .build();
    }

    // --- StateChange round-trip ---

    @Test
    void stateChangeRoundTrip() throws Exception {
        SwitchDevice before = switchDevice("sw-1", false);
        SwitchDevice after = switchDevice("sw-1", true);
        StateChangeEvent event = new StateChangeEvent(before, after, Set.of("isOn"), NOW, "ha");

        BridgeMessage msg = new BridgeMessage.StateChange(TENANCY, NOW, event);

        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("\"@type\":\"STATE_CHANGE\"");

        BridgeMessage deserialized = mapper.readValue(json, BridgeMessage.class);
        assertThat(deserialized).isInstanceOf(BridgeMessage.StateChange.class);

        BridgeMessage.StateChange result = (BridgeMessage.StateChange) deserialized;
        assertThat(result.tenancyId()).isEqualTo(TENANCY);
        assertThat(result.timestamp()).isEqualTo(NOW);
        assertThat(result.event().providerId()).isEqualTo("ha");
        assertThat(((SwitchDevice) result.event().after()).isOn()).isTrue();
    }

    // --- StateSnapshot round-trip ---

    @Test
    void stateSnapshotRoundTrip() throws Exception {
        List<DeviceEntity> devices = List.of(
                switchDevice("sw-1", true),
                switchDevice("sw-2", false)
        );

        BridgeMessage msg = new BridgeMessage.StateSnapshot(TENANCY, NOW, devices);

        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("\"@type\":\"STATE_SNAPSHOT\"");

        BridgeMessage deserialized = mapper.readValue(json, BridgeMessage.class);
        assertThat(deserialized).isInstanceOf(BridgeMessage.StateSnapshot.class);

        BridgeMessage.StateSnapshot result = (BridgeMessage.StateSnapshot) deserialized;
        assertThat(result.tenancyId()).isEqualTo(TENANCY);
        assertThat(result.devices()).hasSize(2);
        assertThat(result.devices().get(0).deviceId()).isEqualTo("sw-1");
        assertThat(result.devices().get(1).deviceId()).isEqualTo("sw-2");
    }

    @Test
    void stateSnapshotDefensiveCopy() {
        var mutable = new java.util.ArrayList<DeviceEntity>(List.of(switchDevice("sw-1", true)));
        BridgeMessage.StateSnapshot snapshot = new BridgeMessage.StateSnapshot(TENANCY, NOW, mutable);
        mutable.add(switchDevice("sw-2", false));
        assertThat(snapshot.devices()).hasSize(1);
    }

    // --- Command round-trip ---

    @Test
    void commandRoundTrip() throws Exception {
        DeviceCommand cmd = DeviceCommand.turnOn("sw-1", Map.of(), "user-1", "corr-1");
        BridgeMessage msg = new BridgeMessage.Command(TENANCY, NOW, "corr-1", cmd);

        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("\"@type\":\"COMMAND\"");

        BridgeMessage deserialized = mapper.readValue(json, BridgeMessage.class);
        assertThat(deserialized).isInstanceOf(BridgeMessage.Command.class);

        BridgeMessage.Command result = (BridgeMessage.Command) deserialized;
        assertThat(result.tenancyId()).isEqualTo(TENANCY);
        assertThat(result.correlationId()).isEqualTo("corr-1");
        assertThat(result.command().targetDeviceId()).isEqualTo("sw-1");
        assertThat(result.command().action()).isEqualTo("turn_on");
    }

    // --- CommandResult round-trip ---

    @Test
    void commandResultRoundTrip() throws Exception {
        BridgeMessage msg = new BridgeMessage.CommandResponse(TENANCY, NOW, "corr-1", CommandResult.SENT);

        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("\"@type\":\"COMMAND_RESULT\"");

        BridgeMessage deserialized = mapper.readValue(json, BridgeMessage.class);
        assertThat(deserialized).isInstanceOf(BridgeMessage.CommandResponse.class);

        BridgeMessage.CommandResponse result = (BridgeMessage.CommandResponse) deserialized;
        assertThat(result.tenancyId()).isEqualTo(TENANCY);
        assertThat(result.correlationId()).isEqualTo("corr-1");
        assertThat(result.result()).isEqualTo(CommandResult.SENT);
    }

    // --- ProviderStatus round-trip ---

    @Test
    void providerStatusRoundTrip() throws Exception {
        ProviderStatusEvent status = new ProviderStatusEvent(
                "ha", ProviderStatus.DISCONNECTED, ProviderStatus.CONNECTED);
        BridgeMessage msg = new BridgeMessage.ProviderStatusChange(TENANCY, NOW, status);

        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("\"@type\":\"PROVIDER_STATUS\"");

        BridgeMessage deserialized = mapper.readValue(json, BridgeMessage.class);
        assertThat(deserialized).isInstanceOf(BridgeMessage.ProviderStatusChange.class);

        BridgeMessage.ProviderStatusChange result = (BridgeMessage.ProviderStatusChange) deserialized;
        assertThat(result.tenancyId()).isEqualTo(TENANCY);
        assertThat(result.status().providerId()).isEqualTo("ha");
        assertThat(result.status().previousStatus()).isEqualTo(ProviderStatus.DISCONNECTED);
        assertThat(result.status().currentStatus()).isEqualTo(ProviderStatus.CONNECTED);
    }

    // --- Heartbeat round-trip ---

    @Test
    void heartbeatRoundTrip() throws Exception {
        BridgeMessage msg = new BridgeMessage.Heartbeat(TENANCY, NOW);

        String json = mapper.writeValueAsString(msg);
        assertThat(json).contains("\"@type\":\"HEARTBEAT\"");

        BridgeMessage deserialized = mapper.readValue(json, BridgeMessage.class);
        assertThat(deserialized).isInstanceOf(BridgeMessage.Heartbeat.class);

        BridgeMessage.Heartbeat result = (BridgeMessage.Heartbeat) deserialized;
        assertThat(result.tenancyId()).isEqualTo(TENANCY);
        assertThat(result.timestamp()).isEqualTo(NOW);
    }

    // --- Null validation ---

    @Test
    void stateChangeRejectsNullFields() {
        StateChangeEvent event = new StateChangeEvent(
                switchDevice("sw-1", false), switchDevice("sw-1", true),
                Set.of("isOn"), NOW, "ha");

        assertThatThrownBy(() -> new BridgeMessage.StateChange(null, NOW, event))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BridgeMessage.StateChange(TENANCY, null, event))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BridgeMessage.StateChange(TENANCY, NOW, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void heartbeatRejectsNullFields() {
        assertThatThrownBy(() -> new BridgeMessage.Heartbeat(null, NOW))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new BridgeMessage.Heartbeat(TENANCY, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void commandRejectsNullCorrelationId() {
        DeviceCommand cmd = DeviceCommand.turnOn("sw-1", Map.of(), "user-1", "corr-1");
        assertThatThrownBy(() -> new BridgeMessage.Command(TENANCY, NOW, null, cmd))
                .isInstanceOf(NullPointerException.class);
    }

    // --- Exhaustive switch ---

    @Test
    void exhaustiveSwitchCoversAllVariants() {
        StateChangeEvent event = new StateChangeEvent(
                switchDevice("sw-1", false), switchDevice("sw-1", true),
                Set.of("isOn"), NOW, "ha");

        List<BridgeMessage> messages = List.of(
                new BridgeMessage.StateChange(TENANCY, NOW, event),
                new BridgeMessage.StateSnapshot(TENANCY, NOW, List.of()),
                new BridgeMessage.ProviderStatusChange(TENANCY, NOW,
                        new ProviderStatusEvent("ha", ProviderStatus.DISCONNECTED, ProviderStatus.CONNECTED)),
                new BridgeMessage.Command(TENANCY, NOW, "c1",
                        DeviceCommand.turnOn("sw-1", Map.of(), "u1", "c1")),
                new BridgeMessage.CommandResponse(TENANCY, NOW, "c1", CommandResult.SENT),
                new BridgeMessage.Heartbeat(TENANCY, NOW)
        );

        // The compiler enforces exhaustiveness on sealed interfaces —
        // if a variant is added without updating this switch, compilation fails.
        for (BridgeMessage msg : messages) {
            String label = switch (msg) {
                case BridgeMessage.StateChange m -> "state_change";
                case BridgeMessage.StateSnapshot m -> "state_snapshot";
                case BridgeMessage.ProviderStatusChange m -> "provider_status";
                case BridgeMessage.Command m -> "command";
                case BridgeMessage.CommandResponse m -> "command_result";
                case BridgeMessage.Heartbeat m -> "heartbeat";
            };
            assertThat(label).isNotEmpty();
        }
    }

    // --- DeviceIdUtils ---

    @Test
    void stripPrefixRemovesTenancyPrefix() {
        assertThat(DeviceIdUtils.stripPrefix("tenant-1/sw-1")).isEqualTo("sw-1");
        assertThat(DeviceIdUtils.stripPrefix("tenant-1/sub/sw-1")).isEqualTo("sub/sw-1");
    }

    @Test
    void stripPrefixReturnsOriginalWhenNoSlash() {
        assertThat(DeviceIdUtils.stripPrefix("sw-1")).isEqualTo("sw-1");
    }

    @Test
    void extractTenancyIdExtractsPrefix() {
        assertThat(DeviceIdUtils.extractTenancyId("tenant-1/sw-1")).isEqualTo("tenant-1");
    }

    @Test
    void extractTenancyIdReturnsOriginalWhenNoSlash() {
        assertThat(DeviceIdUtils.extractTenancyId("sw-1")).isEqualTo("sw-1");
    }

    @Test
    void deviceIdUtilsRejectsNull() {
        assertThatThrownBy(() -> DeviceIdUtils.stripPrefix(null))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> DeviceIdUtils.extractTenancyId(null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- FilterContext ---

    @Test
    void filterContextRejectsNullFields() {
        assertThatThrownBy(() -> new FilterContext(null, ConnectionState.CONNECTED, "ha"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FilterContext(TENANCY, null, "ha"))
                .isInstanceOf(NullPointerException.class);
        assertThatThrownBy(() -> new FilterContext(TENANCY, ConnectionState.CONNECTED, null))
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    void filterContextStoresFields() {
        FilterContext ctx = new FilterContext(TENANCY, ConnectionState.CONNECTED, "ha");
        assertThat(ctx.tenancyId()).isEqualTo(TENANCY);
        assertThat(ctx.connectionState()).isEqualTo(ConnectionState.CONNECTED);
        assertThat(ctx.providerId()).isEqualTo("ha");
    }

    // --- FilterAction ---

    @Test
    void filterActionForwardAndSuppress() {
        FilterAction forward = new FilterAction.Forward();
        FilterAction suppress = new FilterAction.Suppress("rate-limited");

        String label = switch (forward) {
            case FilterAction.Forward f -> "forward";
            case FilterAction.Suppress s -> "suppress: " + s.reason();
        };
        assertThat(label).isEqualTo("forward");

        label = switch (suppress) {
            case FilterAction.Forward f -> "forward";
            case FilterAction.Suppress s -> "suppress: " + s.reason();
        };
        assertThat(label).isEqualTo("suppress: rate-limited");
    }

    @Test
    void filterActionSuppressRejectsNullReason() {
        assertThatThrownBy(() -> new FilterAction.Suppress(null))
                .isInstanceOf(NullPointerException.class);
    }

    // --- ConnectionState ---

    @Test
    void connectionStateValues() {
        assertThat(ConnectionState.values()).containsExactly(
                ConnectionState.CONNECTED, ConnectionState.DISCONNECTED);
    }
}

package io.casehub.iot.bridge.persistence.jpa;

import io.casehub.iot.api.CommandResult;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceCommand;
import io.casehub.iot.api.ProviderStatus;
import io.casehub.iot.api.ProviderStatusEvent;
import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.api.SwitchDevice;
import io.casehub.iot.api.bridge.BridgeAuditEvent;
import io.casehub.iot.api.bridge.BridgeAuditEventType;
import io.casehub.iot.api.bridge.BridgeMessage;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeAuditEventMapperTest {

    private static final Instant NOW = Instant.parse("2026-06-29T12:00:00Z");

    @Test
    void roundTripWithNullMessage() {
        final var event = new BridgeAuditEvent(
            "tenant-1", NOW, BridgeAuditEventType.AGENT_CONNECTED,
            "corr-1", "device-1", null);

        final var entity = BridgeAuditEventMapper.toEntity(event);
        final var back = BridgeAuditEventMapper.toDomain(entity);

        assertThat(back).isEqualTo(event);
    }

    @ParameterizedTest
    @MethodSource("messageVariants")
    void roundTripPreservesMessageVariant(final BridgeMessage message) {
        final var event = new BridgeAuditEvent(
            "tenant-1", NOW, BridgeAuditEventType.STATE_CHANGE,
            null, "device-1", message);

        final var entity = BridgeAuditEventMapper.toEntity(event);
        final var back = BridgeAuditEventMapper.toDomain(entity);

        assertThat(back).isEqualTo(event);
        assertThat(back.message()).isInstanceOf(message.getClass());
    }

    @Test
    void toEntityMapsAllFields() {
        final var event = new BridgeAuditEvent(
            "tenant-1", NOW, BridgeAuditEventType.COMMAND_SENT,
            "corr-99", "light.kitchen", null);

        final var entity = BridgeAuditEventMapper.toEntity(event);

        assertThat(entity.getTenancyId()).isEqualTo("tenant-1");
        assertThat(entity.getReceivedAt()).isEqualTo(NOW);
        assertThat(entity.getEventType()).isEqualTo(BridgeAuditEventType.COMMAND_SENT);
        assertThat(entity.getCorrelationId()).isEqualTo("corr-99");
        assertThat(entity.getDeviceId()).isEqualTo("light.kitchen");
        assertThat(entity.getMessage()).isNull();
    }

    @Test
    void toDomainMapsAllFields() {
        final var entity = new BridgeAuditJpaEntity(
            "tenant-2", NOW, BridgeAuditEventType.AGENT_DISCONNECTED,
            "corr-42", "switch.hall", null);

        final var domain = BridgeAuditEventMapper.toDomain(entity);

        assertThat(domain.tenancyId()).isEqualTo("tenant-2");
        assertThat(domain.receivedAt()).isEqualTo(NOW);
        assertThat(domain.eventType()).isEqualTo(BridgeAuditEventType.AGENT_DISCONNECTED);
        assertThat(domain.correlationId()).isEqualTo("corr-42");
        assertThat(domain.deviceId()).isEqualTo("switch.hall");
        assertThat(domain.message()).isNull();
    }

    static Stream<BridgeMessage> messageVariants() {
        final var device = SwitchDevice.builder()
            .deviceId("switch-1")
            .deviceClass(DeviceClass.SWITCH)
            .label("Test Switch")
            .lastUpdated(NOW)
            .tenancyId("tenant-1")
            .providerId("ha")
            .on(true)
            .build();

        final var stateChange = new StateChangeEvent(
            null, device, Set.of("isOn"), NOW, "ha");

        final var providerStatus = new ProviderStatusEvent(
            "ha", ProviderStatus.DISCONNECTED, ProviderStatus.CONNECTED);

        final var command = DeviceCommand.turnOn("switch-1", Map.of(), "user", "corr-1");

        return Stream.of(
            new BridgeMessage.StateChange("t", NOW, stateChange),
            new BridgeMessage.ReplayedStateChange("t", NOW, stateChange),
            new BridgeMessage.StateSnapshot("t", NOW, List.of(device)),
            new BridgeMessage.ProviderStatusChange("t", NOW, providerStatus),
            new BridgeMessage.Command("t", NOW, "corr-1", command),
            new BridgeMessage.CommandResponse("t", NOW, "corr-1", CommandResult.SENT),
            new BridgeMessage.Heartbeat("t", NOW)
        );
    }
}

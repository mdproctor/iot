package io.casehub.iot.api.bridge;

import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.LightDevice;
import io.casehub.iot.api.StateChangeEvent;
import org.junit.jupiter.api.Test;
import java.time.Instant;
import static org.assertj.core.api.Assertions.assertThat;

class BridgeAuditEventTest {

    @Test
    void stateChangeEventHasMessageAndDeviceId() {
        var now = Instant.now();
        var device = new LightDevice.Builder()
            .deviceId("light.kitchen")
            .deviceClass(DeviceClass.LIGHT)
            .label("Kitchen Light")
            .available(true)
            .lastUpdated(now)
            .tenancyId("tenant-1")
            .providerId("ha")
            .on(true)
            .build();
        var sce = new StateChangeEvent(null, device, device.capabilities().keySet(), now, "ha");
        var msg = new BridgeMessage.StateChange("tenant-1", now, sce);
        var audit = new BridgeAuditEvent(
            "tenant-1", now, BridgeAuditEventType.STATE_CHANGE,
            null, "light.kitchen", msg);
        assertThat(audit.tenancyId()).isEqualTo("tenant-1");
        assertThat(audit.eventType()).isEqualTo(BridgeAuditEventType.STATE_CHANGE);
        assertThat(audit.deviceId()).isEqualTo("light.kitchen");
        assertThat(audit.message()).isNotNull();
        assertThat(audit.correlationId()).isNull();
    }

    @Test
    void connectionEventHasNullMessage() {
        var audit = new BridgeAuditEvent(
            "tenant-1", Instant.now(), BridgeAuditEventType.AGENT_CONNECTED,
            null, null, null);
        assertThat(audit.message()).isNull();
        assertThat(audit.deviceId()).isNull();
    }

    @Test
    void commandSentHasCorrelationId() {
        var audit = new BridgeAuditEvent(
            "tenant-1", Instant.now(), BridgeAuditEventType.COMMAND_SENT,
            "corr-123", "switch.hall", null);
        assertThat(audit.correlationId()).isEqualTo("corr-123");
    }
}

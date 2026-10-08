package io.casehub.iot.openhab;

import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.TriggerSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class OpenHabTriggerClassificationTest {

    @Test
    void presenceSensorDeviceClass_motion() {
        assertThat(OpenHabSseClient.classifyTrigger(DeviceClass.PRESENCE_SENSOR))
                .isEqualTo(TriggerSource.MOTION);
    }

    @Test
    void lightDeviceClass_unknown() {
        assertThat(OpenHabSseClient.classifyTrigger(DeviceClass.LIGHT))
                .isEqualTo(TriggerSource.UNKNOWN);
    }

    @Test
    void lockDeviceClass_unknown() {
        assertThat(OpenHabSseClient.classifyTrigger(DeviceClass.LOCK))
                .isEqualTo(TriggerSource.UNKNOWN);
    }

    @Test
    void thermostatDeviceClass_unknown() {
        assertThat(OpenHabSseClient.classifyTrigger(DeviceClass.THERMOSTAT))
                .isEqualTo(TriggerSource.UNKNOWN);
    }

    @Test
    void nullDeviceClass_unknown() {
        assertThat(OpenHabSseClient.classifyTrigger(null))
                .isEqualTo(TriggerSource.UNKNOWN);
    }
}

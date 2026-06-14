package io.casehub.iot.openhab;

import io.casehub.iot.api.DeviceCommand;
import io.casehub.iot.api.Temperature;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHabCommandDispatchTest {

    private final OpenHabProvider provider = new OpenHabProvider();

    // ---- 1. turn_on → "ON" ----

    @Test
    void turnOnProducesOn() {
        var command = DeviceCommand.turnOn("switch1", Map.of(), "test", "corr-1");
        assertThat(provider.buildCommandValue(command)).isEqualTo("ON");
    }

    // ---- 2. turn_off → "OFF" ----

    @Test
    void turnOffProducesOff() {
        var command = DeviceCommand.turnOff("switch1", "test", "corr-2");
        assertThat(provider.buildCommandValue(command)).isEqualTo("OFF");
    }

    // ---- 3. lock → "ON" ----

    @Test
    void lockProducesOn() {
        var command = DeviceCommand.lock("lock1", "test", "corr-3");
        assertThat(provider.buildCommandValue(command)).isEqualTo("ON");
    }

    // ---- 4. unlock → "OFF" ----

    @Test
    void unlockProducesOff() {
        var command = DeviceCommand.unlock("lock1", "test", "corr-4");
        assertThat(provider.buildCommandValue(command)).isEqualTo("OFF");
    }

    // ---- 5. set_temperature → temperature value string ----

    @Test
    void setTemperatureProducesValueString() {
        var command = DeviceCommand.setTemperature("therm1",
                new Temperature(new BigDecimal("22.5"), Temperature.TemperatureUnit.CELSIUS),
                "test", "corr-5");
        assertThat(provider.buildCommandValue(command)).isEqualTo("22.5");
    }

    // ---- 6. set_position inverts value ----

    @Test
    void setPositionInvertsValue() {
        var command = DeviceCommand.setPosition("cover1", 70, "test", "corr-6");
        assertThat(provider.buildCommandValue(command)).isEqualTo("30");
    }

    // ---- 7. set_position 0 → 100 ----

    @Test
    void setPositionZeroProducesHundred() {
        var command = DeviceCommand.setPosition("cover1", 0, "test", "corr-7");
        assertThat(provider.buildCommandValue(command)).isEqualTo("100");
    }

    // ---- 8. set_volume → volume value string ----

    @Test
    void setVolumeProducesValueString() {
        var command = DeviceCommand.setVolume("media1", 80, "test", "corr-8");
        assertThat(provider.buildCommandValue(command)).isEqualTo("80");
    }

    // ---- 9. unknown action → null ----

    @Test
    void unknownActionProducesNull() {
        var command = new DeviceCommand("device1", "unknown_action", Map.of(), "test", "corr-9");
        assertThat(provider.buildCommandValue(command)).isNull();
    }
}

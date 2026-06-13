package io.casehub.iot.testing.handlers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.iot.api.*;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class CommonHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DeviceFixtureDefaults DEFAULTS = DeviceFixtureDefaults.DEFAULT;

    @Test
    void switchHandler() {
        var handler = new SwitchHandler();
        assertThat(handler.typeName()).isEqualTo("switch");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.SWITCH);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "sw-1").put("label", "Test Switch").put("on", true);
        var device = (SwitchDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.deviceId()).isEqualTo("sw-1");
        assertThat(device.label()).isEqualTo("Test Switch");
        assertThat(device.isOn()).isTrue();
        assertThat(device.deviceClass()).isEqualTo(DeviceClass.SWITCH);
        assertThat(device.tenancyId()).isEqualTo(Fixtures.DEFAULT_TENANT);
        assertThat(device.lastUpdated()).isEqualTo(Fixtures.EPOCH);
        assertThat(device.available()).isTrue();
    }

    @Test
    void switchHandlerOnDefaultsFalse() {
        var handler = new SwitchHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "sw-1").put("label", "Test");
        var device = (SwitchDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.isOn()).isFalse();
    }

    @Test
    void lightHandler() {
        var handler = new LightHandler();
        assertThat(handler.typeName()).isEqualTo("light");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.LIGHT);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "lt-1").put("label", "Test Light")
            .put("on", true).put("brightness", 80).put("colorTemp", 4000);
        var device = (LightDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.isOn()).isTrue();
        assertThat(device.brightness()).hasValue(80);
        assertThat(device.colorTemp()).hasValue(4000);
    }

    @Test
    void lightHandlerOptionalFieldsAbsent() {
        var handler = new LightHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "lt-1").put("label", "Test Light").put("on", false);
        var device = (LightDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.brightness()).isEmpty();
        assertThat(device.colorTemp()).isEmpty();
    }

    @Test
    void thermostatHandler() {
        var handler = new ThermostatHandler();
        assertThat(handler.typeName()).isEqualTo("thermostat");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.THERMOSTAT);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "th-1").put("label", "Test Thermostat").put("mode", "HEAT");
        node.putObject("currentTemperature").put("value", 21).put("unit", "CELSIUS");
        node.putObject("targetTemperature").put("value", 22).put("unit", "CELSIUS");

        var device = (ThermostatDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.currentTemperature()).isEqualTo(
            new Temperature(new BigDecimal("21"), Temperature.TemperatureUnit.CELSIUS));
        assertThat(device.targetTemperature()).isEqualTo(
            new Temperature(new BigDecimal("22"), Temperature.TemperatureUnit.CELSIUS));
        assertThat(device.mode()).isEqualTo(ThermostatMode.HEAT);
    }

    @Test
    void sensorHandler() {
        var handler = new SensorHandler();
        assertThat(handler.typeName()).isEqualTo("sensor");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.SENSOR);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "sn-1").put("label", "Test Sensor")
            .put("sensorType", "TEMPERATURE").put("numericValue", 15).put("unit", "C");
        var device = (SensorDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.sensorType()).isEqualTo(SensorType.TEMPERATURE);
        assertThat(device.numericValue()).hasValue(new BigDecimal("15"));
        assertThat(device.unit()).hasValue("C");
    }

    @Test
    void sensorHandlerOptionalFieldsAbsent() {
        var handler = new SensorHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "sn-1").put("label", "Test Sensor")
            .put("sensorType", "TEMPERATURE");
        var device = (SensorDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.numericValue()).isEmpty();
        assertThat(device.unit()).isEmpty();
        assertThat(device.binaryValue()).isEmpty();
    }

    @Test
    void sensorHandlerBinaryValue() {
        var handler = new SensorHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "sn-1").put("label", "Test Sensor")
            .put("sensorType", "DOOR_WINDOW").put("binaryValue", true);
        var device = (SensorDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.binaryValue()).hasValue(true);
    }

    @Test
    void presenceSensorHandler() {
        var handler = new PresenceSensorHandler();
        assertThat(handler.typeName()).isEqualTo("presence_sensor");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.PRESENCE_SENSOR);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "ps-1").put("label", "Test Presence")
            .put("present", true).put("lastSeen", "2026-06-01T12:00:00Z");
        var device = (PresenceSensor) handler.fromYaml(node, DEFAULTS);
        assertThat(device.isPresent()).isTrue();
        assertThat(device.lastSeen()).isEqualTo(Instant.parse("2026-06-01T12:00:00Z"));
    }

    @Test
    void powerSensorHandler() {
        var handler = new PowerSensorHandler();
        assertThat(handler.typeName()).isEqualTo("power_sensor");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.POWER_SENSOR);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "pw-1").put("label", "Test Power")
            .put("power", 3200).put("energy", 12500);
        var device = (PowerSensor) handler.fromYaml(node, DEFAULTS);
        assertThat(device.power()).hasValue(new BigDecimal("3200"));
        assertThat(device.energy()).hasValue(new BigDecimal("12500"));
    }

    @Test
    void powerSensorHandlerOptionalFieldsAbsent() {
        var handler = new PowerSensorHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "pw-1").put("label", "Test Power");
        var device = (PowerSensor) handler.fromYaml(node, DEFAULTS);
        assertThat(device.power()).isEmpty();
        assertThat(device.energy()).isEmpty();
    }

    @Test
    void lockHandler() {
        var handler = new LockHandler();
        assertThat(handler.typeName()).isEqualTo("lock");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.LOCK);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "lk-1").put("label", "Test Lock").put("locked", true);
        var device = (LockDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.isLocked()).isTrue();
    }

    @Test
    void coverHandler() {
        var handler = new CoverHandler();
        assertThat(handler.typeName()).isEqualTo("cover");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.COVER);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "cv-1").put("label", "Test Cover")
            .put("position", 50).put("moving", true);
        var device = (CoverDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.position()).hasValue(50);
        assertThat(device.isMoving()).isTrue();
    }

    @Test
    void coverHandlerOptionalFieldsAbsent() {
        var handler = new CoverHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "cv-1").put("label", "Test Cover");
        var device = (CoverDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.position()).isEmpty();
        assertThat(device.isMoving()).isFalse();
    }

    @Test
    void mediaPlayerHandler() {
        var handler = new MediaPlayerHandler();
        assertThat(handler.typeName()).isEqualTo("media_player");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.MEDIA_PLAYER);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "mp-1").put("label", "Test Media")
            .put("playing", true).put("volume", 65);
        var device = (MediaPlayerDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.isPlaying()).isTrue();
        assertThat(device.volume()).hasValue(65);
    }

    @Test
    void mediaPlayerHandlerOptionalFieldsAbsent() {
        var handler = new MediaPlayerHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "mp-1").put("label", "Test Media");
        var device = (MediaPlayerDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.isPlaying()).isFalse();
        assertThat(device.volume()).isEmpty();
    }

    @Test
    void fanHandler() {
        var handler = new FanHandler();
        assertThat(handler.typeName()).isEqualTo("fan");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.FAN);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "fn-1").put("label", "Test Fan")
            .put("on", true).put("speed", 3);
        var device = (FanDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.isOn()).isTrue();
        assertThat(device.speed()).hasValue(3);
    }

    @Test
    void fanHandlerOptionalFieldsAbsent() {
        var handler = new FanHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "fn-1").put("label", "Test Fan");
        var device = (FanDevice) handler.fromYaml(node, DEFAULTS);
        assertThat(device.isOn()).isFalse();
        assertThat(device.speed()).isEmpty();
    }

    @Test
    void commonFieldsUseDefaults() {
        var customDefaults = new DeviceFixtureDefaults("custom-tenant",
            Instant.parse("2026-06-01T00:00:00Z"), false);
        var handler = new SwitchHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "sw-1").put("label", "Test");
        var device = handler.fromYaml(node, customDefaults);
        assertThat(device.tenancyId()).isEqualTo("custom-tenant");
        assertThat(device.lastUpdated()).isEqualTo(Instant.parse("2026-06-01T00:00:00Z"));
        assertThat(device.available()).isFalse();
    }

    @Test
    void commonFieldsPerDeviceOverridesDefaults() {
        var handler = new SwitchHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "sw-1").put("label", "Test")
            .put("tenancyId", "override").put("available", false)
            .put("lastUpdated", "2026-12-01T00:00:00Z");
        var device = handler.fromYaml(node, DEFAULTS);
        assertThat(device.tenancyId()).isEqualTo("override");
        assertThat(device.available()).isFalse();
        assertThat(device.lastUpdated()).isEqualTo(Instant.parse("2026-12-01T00:00:00Z"));
    }
}

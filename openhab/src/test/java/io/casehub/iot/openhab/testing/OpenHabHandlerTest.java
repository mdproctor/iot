package io.casehub.iot.openhab.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.Temperature;
import io.casehub.iot.api.ThermostatMode;
import io.casehub.iot.openhab.OpenHabHsbType;
import io.casehub.iot.openhab.OpenHabLight;
import io.casehub.iot.openhab.OpenHabRollershutter;
import io.casehub.iot.openhab.OpenHabThermostat;
import io.casehub.iot.openhab.OpenHabUpDownType;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHabHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DeviceFixtureDefaults DEFAULTS = DeviceFixtureDefaults.DEFAULT;

    @Test
    void lightHandler() {
        var handler = new OpenHabLightHandler();
        assertThat(handler.typeName()).isEqualTo("openhab:light");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.LIGHT);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "oh-lt-1").put("label", "OH Light")
            .put("on", true).put("brightness", 75);
        ObjectNode hsb = node.putObject("hsb");
        hsb.put("hue", 240).put("saturation", 80).put("brightness", 75);

        var device = (OpenHabLight) handler.fromYaml(node, DEFAULTS);
        assertThat(device.isOn()).isTrue();
        assertThat(device.brightness()).hasValue(75);
        assertThat(device.hsb()).isPresent();
        var hsbVal = device.hsb().get();
        assertThat(hsbVal.hue()).isEqualByComparingTo(new BigDecimal("240"));
        assertThat(hsbVal.saturation()).isEqualByComparingTo(new BigDecimal("80"));
        assertThat(hsbVal.brightness()).isEqualByComparingTo(new BigDecimal("75"));
    }

    @Test
    void lightHandlerOptionalFieldsAbsent() {
        var handler = new OpenHabLightHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "oh-lt-1").put("label", "OH Light").put("on", false);
        var device = (OpenHabLight) handler.fromYaml(node, DEFAULTS);
        assertThat(device.hsb()).isEmpty();
    }

    @Test
    void thermostatHandler() {
        var handler = new OpenHabThermostatHandler();
        assertThat(handler.typeName()).isEqualTo("openhab:thermostat");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.THERMOSTAT);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "oh-th-1").put("label", "OH Thermostat")
            .put("mode", "HEAT")
            .put("heatingDemand", 75).put("coolingDemand", 0);
        node.putObject("currentTemperature").put("value", 19).put("unit", "CELSIUS");
        node.putObject("targetTemperature").put("value", 21).put("unit", "CELSIUS");

        var device = (OpenHabThermostat) handler.fromYaml(node, DEFAULTS);
        assertThat(device.mode()).isEqualTo(ThermostatMode.HEAT);
        assertThat(device.heatingDemand()).hasValue(new BigDecimal("75"));
        assertThat(device.coolingDemand()).hasValue(new BigDecimal("0"));
    }

    @Test
    void thermostatHandlerOptionalFieldsAbsent() {
        var handler = new OpenHabThermostatHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "oh-th-1").put("label", "OH Thermostat").put("mode", "COOL");
        node.putObject("currentTemperature").put("value", 25).put("unit", "CELSIUS");
        node.putObject("targetTemperature").put("value", 22).put("unit", "CELSIUS");
        var device = (OpenHabThermostat) handler.fromYaml(node, DEFAULTS);
        assertThat(device.heatingDemand()).isEmpty();
        assertThat(device.coolingDemand()).isEmpty();
    }

    @Test
    void rollershutterHandler() {
        var handler = new OpenHabRollershutterHandler();
        assertThat(handler.typeName()).isEqualTo("openhab:cover");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.COVER);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "oh-cv-1").put("label", "OH Rollershutter")
            .put("position", 30).put("moving", true).put("upDown", "DOWN");
        var device = (OpenHabRollershutter) handler.fromYaml(node, DEFAULTS);
        assertThat(device.position()).hasValue(30);
        assertThat(device.isMoving()).isTrue();
        assertThat(device.upDown()).hasValue(OpenHabUpDownType.DOWN);
    }

    @Test
    void rollershutterHandlerOptionalFieldsAbsent() {
        var handler = new OpenHabRollershutterHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "oh-cv-1").put("label", "OH Cover");
        var device = (OpenHabRollershutter) handler.fromYaml(node, DEFAULTS);
        assertThat(device.upDown()).isEmpty();
        assertThat(device.position()).isEmpty();
    }
}

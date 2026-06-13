package io.casehub.iot.homeassistant.testing;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.Temperature;
import io.casehub.iot.api.ThermostatMode;
import io.casehub.iot.homeassistant.HomeAssistantLight;
import io.casehub.iot.homeassistant.HomeAssistantLock;
import io.casehub.iot.homeassistant.HomeAssistantThermostat;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HomeAssistantHandlerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final DeviceFixtureDefaults DEFAULTS = DeviceFixtureDefaults.DEFAULT;

    @Test
    void lightHandler() {
        var handler = new HomeAssistantLightHandler();
        assertThat(handler.typeName()).isEqualTo("homeassistant:light");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.LIGHT);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "ha-lt-1").put("label", "HA Light")
            .put("on", true).put("brightness", 75).put("colorTemp", 3000)
            .put("effect", "rainbow");
        ArrayNode rgb = node.putArray("rgbColor");
        rgb.add(255).add(128).add(0);
        ArrayNode modes = node.putArray("supportedColorModes");
        modes.add("color_temp").add("rgb");

        var device = (HomeAssistantLight) handler.fromYaml(node, DEFAULTS);
        assertThat(device.isOn()).isTrue();
        assertThat(device.brightness()).hasValue(75);
        assertThat(device.colorTemp()).hasValue(3000);
        assertThat(device.rgbColor()).isPresent();
        assertThat(device.rgbColor().get()).containsExactly(255, 128, 0);
        assertThat(device.effect()).hasValue("rainbow");
        assertThat(device.supportedColorModes()).containsExactlyInAnyOrder("color_temp", "rgb");
    }

    @Test
    void lightHandlerOptionalFieldsAbsent() {
        var handler = new HomeAssistantLightHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "ha-lt-1").put("label", "HA Light").put("on", false);
        var device = (HomeAssistantLight) handler.fromYaml(node, DEFAULTS);
        assertThat(device.rgbColor()).isEmpty();
        assertThat(device.effect()).isEmpty();
        assertThat(device.supportedColorModes()).isEmpty();
    }

    @Test
    void thermostatHandler() {
        var handler = new HomeAssistantThermostatHandler();
        assertThat(handler.typeName()).isEqualTo("homeassistant:thermostat");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.THERMOSTAT);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "ha-th-1").put("label", "HA Thermostat")
            .put("mode", "AUTO")
            .put("presetMode", "eco").put("swingMode", "vertical")
            .put("hvacAction", "heating");
        node.putObject("currentTemperature").put("value", 20).put("unit", "CELSIUS");
        node.putObject("targetTemperature").put("value", 23).put("unit", "CELSIUS");

        var device = (HomeAssistantThermostat) handler.fromYaml(node, DEFAULTS);
        assertThat(device.mode()).isEqualTo(ThermostatMode.AUTO);
        assertThat(device.presetMode()).hasValue("eco");
        assertThat(device.swingMode()).hasValue("vertical");
        assertThat(device.hvacAction()).hasValue("heating");
    }

    @Test
    void lockHandler() {
        var handler = new HomeAssistantLockHandler();
        assertThat(handler.typeName()).isEqualTo("homeassistant:lock");
        assertThat(handler.deviceClass()).isEqualTo(DeviceClass.LOCK);

        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "ha-lk-1").put("label", "HA Lock")
            .put("locked", true).put("changedBy", "user_1").put("codeSlot", 3);
        var device = (HomeAssistantLock) handler.fromYaml(node, DEFAULTS);
        assertThat(device.isLocked()).isTrue();
        assertThat(device.changedBy()).hasValue("user_1");
        assertThat(device.codeSlot()).hasValue(3);
    }

    @Test
    void lockHandlerOptionalFieldsAbsent() {
        var handler = new HomeAssistantLockHandler();
        ObjectNode node = MAPPER.createObjectNode()
            .put("deviceId", "ha-lk-1").put("label", "HA Lock").put("locked", false);
        var device = (HomeAssistantLock) handler.fromYaml(node, DEFAULTS);
        assertThat(device.changedBy()).isEmpty();
        assertThat(device.codeSlot()).isEmpty();
    }
}

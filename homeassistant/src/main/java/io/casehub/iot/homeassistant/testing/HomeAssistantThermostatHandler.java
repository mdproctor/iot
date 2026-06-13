package io.casehub.iot.homeassistant.testing;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.Temperature;
import io.casehub.iot.api.ThermostatMode;
import io.casehub.iot.homeassistant.HomeAssistantThermostat;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;
import io.casehub.iot.testing.handlers.ThermostatHandler;

import java.math.BigDecimal;

public final class HomeAssistantThermostatHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "homeassistant:thermostat"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.THERMOSTAT; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        var builder = HomeAssistantThermostat.builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.currentTemperature(ThermostatHandler.parseTemperature(node.get("currentTemperature")));
        builder.targetTemperature(ThermostatHandler.parseTemperature(node.get("targetTemperature")));
        builder.mode(ThermostatMode.valueOf(node.get("mode").asText()));
        if (node.has("presetMode")) builder.presetMode(node.get("presetMode").asText());
        if (node.has("swingMode")) builder.swingMode(node.get("swingMode").asText());
        if (node.has("hvacAction")) builder.hvacAction(node.get("hvacAction").asText());
        return builder.build();
    }
}

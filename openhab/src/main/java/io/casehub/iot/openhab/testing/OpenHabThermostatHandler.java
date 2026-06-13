package io.casehub.iot.openhab.testing;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.ThermostatMode;
import io.casehub.iot.openhab.OpenHabThermostat;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;
import io.casehub.iot.testing.handlers.ThermostatHandler;

import java.math.BigDecimal;

public final class OpenHabThermostatHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "openhab:thermostat"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.THERMOSTAT; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        var builder = OpenHabThermostat.builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.currentTemperature(ThermostatHandler.parseTemperature(node.get("currentTemperature")));
        builder.targetTemperature(ThermostatHandler.parseTemperature(node.get("targetTemperature")));
        builder.mode(ThermostatMode.valueOf(node.get("mode").asText()));
        if (node.has("heatingDemand")) builder.heatingDemand(new BigDecimal(node.get("heatingDemand").asText()));
        if (node.has("coolingDemand")) builder.coolingDemand(new BigDecimal(node.get("coolingDemand").asText()));
        return builder.build();
    }
}

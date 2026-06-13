package io.casehub.iot.testing.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.Temperature;
import io.casehub.iot.api.ThermostatDevice;
import io.casehub.iot.api.ThermostatMode;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

import java.math.BigDecimal;

public final class ThermostatHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "thermostat"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.THERMOSTAT; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        var builder = new ThermostatDevice.Builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.currentTemperature(parseTemperature(node.get("currentTemperature")));
        builder.targetTemperature(parseTemperature(node.get("targetTemperature")));
        builder.mode(ThermostatMode.valueOf(node.get("mode").asText()));
        return builder.build();
    }

    public static Temperature parseTemperature(JsonNode node) {
        return new Temperature(
            new BigDecimal(node.get("value").asText()),
            Temperature.TemperatureUnit.valueOf(node.get("unit").asText()));
    }
}

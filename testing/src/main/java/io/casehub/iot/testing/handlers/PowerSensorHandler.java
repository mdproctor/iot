package io.casehub.iot.testing.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.PowerSensor;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

import java.math.BigDecimal;

public final class PowerSensorHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "power_sensor"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.POWER_SENSOR; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        PowerSensor.Builder builder = PowerSensor.builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        if (node.has("power")) builder.power(new BigDecimal(node.get("power").asText()));
        if (node.has("energy")) builder.energy(new BigDecimal(node.get("energy").asText()));
        return builder.build();
    }
}

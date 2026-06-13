package io.casehub.iot.testing.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.SensorDevice;
import io.casehub.iot.api.SensorType;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

import java.math.BigDecimal;

public final class SensorHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "sensor"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.SENSOR; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        SensorDevice.Builder builder = SensorDevice.builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.sensorType(SensorType.valueOf(node.get("sensorType").asText()));
        if (node.has("numericValue")) builder.numericValue(new BigDecimal(node.get("numericValue").asText()));
        if (node.has("unit")) builder.unit(node.get("unit").asText());
        if (node.has("binaryValue")) builder.binaryValue(node.get("binaryValue").asBoolean());
        return builder.build();
    }
}

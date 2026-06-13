package io.casehub.iot.testing.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.PresenceSensor;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

import java.time.Instant;

public final class PresenceSensorHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "presence_sensor"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.PRESENCE_SENSOR; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        PresenceSensor.Builder builder = PresenceSensor.builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.present(node.has("present") && node.get("present").asBoolean());
        builder.lastSeen(node.has("lastSeen")
            ? Instant.parse(node.get("lastSeen").asText()) : defaults.lastUpdated());
        return builder.build();
    }
}

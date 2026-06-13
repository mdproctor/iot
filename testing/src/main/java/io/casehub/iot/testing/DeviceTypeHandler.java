package io.casehub.iot.testing;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;

import java.time.Instant;

public interface DeviceTypeHandler {

    String typeName();

    DeviceClass deviceClass();

    DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults);

    static <B extends DeviceEntity.Builder<?, B>> B applyCommonFields(
            B builder, JsonNode node, DeviceFixtureDefaults defaults, DeviceClass deviceClass) {
        if (!node.has("deviceId")) {
            throw new IllegalArgumentException("Missing required field 'deviceId'");
        }
        if (!node.has("label")) {
            throw new IllegalArgumentException(
                "Missing required field 'label' (device: " + node.get("deviceId").asText() + ")");
        }
        builder.deviceId(node.get("deviceId").asText());
        builder.deviceClass(deviceClass);
        builder.label(node.get("label").asText());
        builder.available(node.has("available")
            ? node.get("available").asBoolean() : defaults.available());
        builder.lastUpdated(node.has("lastUpdated")
            ? Instant.parse(node.get("lastUpdated").asText()) : defaults.lastUpdated());
        builder.tenancyId(node.has("tenancyId")
            ? node.get("tenancyId").asText() : defaults.tenancyId());
        return builder;
    }
}

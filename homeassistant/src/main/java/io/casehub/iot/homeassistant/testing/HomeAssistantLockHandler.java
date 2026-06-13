package io.casehub.iot.homeassistant.testing;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.homeassistant.HomeAssistantLock;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

public final class HomeAssistantLockHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "homeassistant:lock"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.LOCK; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        var builder = HomeAssistantLock.builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.locked(node.has("locked") && node.get("locked").asBoolean());
        if (node.has("changedBy")) builder.changedBy(node.get("changedBy").asText());
        if (node.has("codeSlot")) builder.codeSlot(node.get("codeSlot").intValue());
        return builder.build();
    }
}

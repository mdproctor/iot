package io.casehub.iot.testing.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.FanDevice;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

public final class FanHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "fan"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.FAN; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        FanDevice.Builder builder = FanDevice.builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.on(node.has("on") && node.get("on").asBoolean());
        if (node.has("speed")) builder.speed(node.get("speed").intValue());
        return builder.build();
    }
}

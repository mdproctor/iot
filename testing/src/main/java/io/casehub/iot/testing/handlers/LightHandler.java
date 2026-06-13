package io.casehub.iot.testing.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.LightDevice;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

public final class LightHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "light"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.LIGHT; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        var builder = new LightDevice.Builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.on(node.has("on") && node.get("on").asBoolean());
        if (node.has("brightness")) builder.brightness(node.get("brightness").intValue());
        if (node.has("colorTemp")) builder.colorTemp(node.get("colorTemp").intValue());
        return builder.build();
    }
}

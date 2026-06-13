package io.casehub.iot.testing.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.CoverDevice;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

public final class CoverHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "cover"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.COVER; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        var builder = new CoverDevice.Builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        if (node.has("position")) builder.position(node.get("position").intValue());
        builder.moving(node.has("moving") && node.get("moving").asBoolean());
        return builder.build();
    }
}

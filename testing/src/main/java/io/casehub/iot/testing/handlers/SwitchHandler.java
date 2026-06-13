package io.casehub.iot.testing.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.SwitchDevice;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

public final class SwitchHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "switch"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.SWITCH; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        SwitchDevice.Builder builder = SwitchDevice.builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.on(node.has("on") && node.get("on").asBoolean());
        return builder.build();
    }
}

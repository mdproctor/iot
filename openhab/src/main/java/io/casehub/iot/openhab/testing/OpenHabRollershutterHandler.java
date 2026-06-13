package io.casehub.iot.openhab.testing;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.openhab.OpenHabRollershutter;
import io.casehub.iot.openhab.OpenHabUpDownType;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

public final class OpenHabRollershutterHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "openhab:cover"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.COVER; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        var builder = OpenHabRollershutter.builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        if (node.has("position")) builder.position(node.get("position").intValue());
        builder.moving(node.has("moving") && node.get("moving").asBoolean());
        if (node.has("upDown")) builder.upDown(OpenHabUpDownType.valueOf(node.get("upDown").asText()));
        return builder.build();
    }
}

package io.casehub.iot.openhab.testing;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.openhab.OpenHabHsbType;
import io.casehub.iot.openhab.OpenHabLight;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

import java.math.BigDecimal;

public final class OpenHabLightHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "openhab:light"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.LIGHT; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        var builder = OpenHabLight.builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.on(node.has("on") && node.get("on").asBoolean());
        if (node.has("brightness")) builder.brightness(node.get("brightness").intValue());
        if (node.has("colorTemp")) builder.colorTemp(node.get("colorTemp").intValue());
        if (node.has("hsb")) {
            JsonNode hsb = node.get("hsb");
            builder.hsb(new OpenHabHsbType(
                new BigDecimal(hsb.get("hue").asText()),
                new BigDecimal(hsb.get("saturation").asText()),
                new BigDecimal(hsb.get("brightness").asText())));
        }
        return builder.build();
    }
}

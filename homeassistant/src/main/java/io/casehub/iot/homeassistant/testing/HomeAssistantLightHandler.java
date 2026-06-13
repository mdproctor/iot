package io.casehub.iot.homeassistant.testing;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.homeassistant.HomeAssistantLight;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

import java.util.HashSet;
import java.util.Set;

public final class HomeAssistantLightHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "homeassistant:light"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.LIGHT; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        var builder = HomeAssistantLight.builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.on(node.has("on") && node.get("on").asBoolean());
        if (node.has("brightness")) builder.brightness(node.get("brightness").intValue());
        if (node.has("colorTemp")) builder.colorTemp(node.get("colorTemp").intValue());
        if (node.has("rgbColor")) {
            JsonNode rgb = node.get("rgbColor");
            builder.rgbColor(new int[]{rgb.get(0).intValue(), rgb.get(1).intValue(), rgb.get(2).intValue()});
        }
        if (node.has("effect")) builder.effect(node.get("effect").asText());
        if (node.has("supportedColorModes")) {
            Set<String> modes = new HashSet<>();
            node.get("supportedColorModes").forEach(n -> modes.add(n.asText()));
            builder.supportedColorModes(modes);
        }
        return builder.build();
    }
}

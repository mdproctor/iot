package io.casehub.iot.testing.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.MediaPlayerDevice;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

public final class MediaPlayerHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "media_player"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.MEDIA_PLAYER; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        MediaPlayerDevice.Builder builder = MediaPlayerDevice.builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.playing(node.has("playing") && node.get("playing").asBoolean());
        if (node.has("volume")) builder.volume(node.get("volume").intValue());
        return builder.build();
    }
}

package io.casehub.iot.testing.handlers;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.LockDevice;
import io.casehub.iot.testing.DeviceFixtureDefaults;
import io.casehub.iot.testing.DeviceTypeHandler;

public final class LockHandler implements DeviceTypeHandler {

    @Override public String typeName() { return "lock"; }
    @Override public DeviceClass deviceClass() { return DeviceClass.LOCK; }

    @Override
    public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
        var builder = new LockDevice.Builder();
        DeviceTypeHandler.applyCommonFields(builder, node, defaults, deviceClass());
        builder.locked(node.has("locked") && node.get("locked").asBoolean());
        return builder.build();
    }
}

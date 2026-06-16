package io.casehub.iot.bridge.server;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.casehub.iot.api.DeviceEntity;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Objects;

@ApplicationScoped
public class DeviceIdNamespacer {

    private final ObjectMapper mapper;

    @Inject
    public DeviceIdNamespacer(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
    }

    /**
     * Return a copy of {@code device} with its {@code deviceId} prefixed as
     * {@code tenancyId + "/" + originalDeviceId}.
     */
    public DeviceEntity namespace(DeviceEntity device, String tenancyId) {
        Objects.requireNonNull(device, "device");
        Objects.requireNonNull(tenancyId, "tenancyId");
        try {
            ObjectNode tree = mapper.valueToTree(device);
            tree.put("deviceId", tenancyId + "/" + device.deviceId());
            return mapper.treeToValue(tree, DeviceEntity.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to namespace device " + device.deviceId() + " with tenancy " + tenancyId, e);
        }
    }

    /**
     * Return a copy of {@code device} with {@code available} set to {@code false}.
     */
    public DeviceEntity markUnavailable(DeviceEntity device) {
        Objects.requireNonNull(device, "device");
        try {
            ObjectNode tree = mapper.valueToTree(device);
            tree.put("available", false);
            return mapper.treeToValue(tree, DeviceEntity.class);
        } catch (Exception e) {
            throw new IllegalStateException(
                    "Failed to mark device " + device.deviceId() + " unavailable", e);
        }
    }
}

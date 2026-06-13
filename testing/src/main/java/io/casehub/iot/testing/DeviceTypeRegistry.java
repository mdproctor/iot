package io.casehub.iot.testing;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.ServiceLoader;
import java.util.stream.Collectors;

public final class DeviceTypeRegistry {

    private final Map<String, DeviceTypeHandler> handlers;

    public DeviceTypeRegistry(Iterable<DeviceTypeHandler> handlers) {
        this.handlers = new LinkedHashMap<>();
        for (DeviceTypeHandler handler : handlers) {
            DeviceTypeHandler existing = this.handlers.put(handler.typeName(), handler);
            if (existing != null) {
                throw new IllegalArgumentException(
                    "Duplicate DeviceTypeHandler for type '" + handler.typeName()
                    + "': " + existing.getClass().getName()
                    + " and " + handler.getClass().getName());
            }
        }
    }

    public DeviceTypeHandler handlerFor(String typeName) {
        DeviceTypeHandler handler = handlers.get(typeName);
        if (handler == null) {
            String registered = handlers.keySet().stream()
                .sorted().collect(Collectors.joining(", "));
            throw new IllegalArgumentException(
                "Unknown device type '" + typeName
                + "'. Registered types: [" + registered + "]");
        }
        return handler;
    }

    public static DeviceTypeRegistry discover() {
        return new DeviceTypeRegistry(ServiceLoader.load(DeviceTypeHandler.class));
    }
}

package io.casehub.iot.testing;

import com.fasterxml.jackson.databind.JsonNode;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceTypeRegistryTest {

    static final class StubHandler implements DeviceTypeHandler {
        private final String typeName;
        private final DeviceClass deviceClass;

        StubHandler(String typeName, DeviceClass deviceClass) {
            this.typeName = typeName;
            this.deviceClass = deviceClass;
        }

        @Override public String typeName() { return typeName; }
        @Override public DeviceClass deviceClass() { return deviceClass; }
        @Override public DeviceEntity fromYaml(JsonNode node, DeviceFixtureDefaults defaults) {
            return null;
        }
    }

    @Test
    void handlerForReturnsRegisteredHandler() {
        var handler = new StubHandler("switch", DeviceClass.SWITCH);
        var registry = new DeviceTypeRegistry(List.of(handler));
        assertThat(registry.handlerFor("switch")).isSameAs(handler);
    }

    @Test
    void handlerForUnknownTypeThrowsWithRegisteredTypes() {
        var registry = new DeviceTypeRegistry(
            List.of(new StubHandler("switch", DeviceClass.SWITCH)));
        assertThatThrownBy(() -> registry.handlerFor("unknown"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown device type 'unknown'")
            .hasMessageContaining("switch");
    }

    @Test
    void duplicateTypeNameThrowsAtConstruction() {
        assertThatThrownBy(() -> new DeviceTypeRegistry(List.of(
            new StubHandler("switch", DeviceClass.SWITCH),
            new StubHandler("switch", DeviceClass.SWITCH))))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Duplicate")
            .hasMessageContaining("switch");
    }
}

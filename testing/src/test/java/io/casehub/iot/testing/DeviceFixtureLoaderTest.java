package io.casehub.iot.testing;

import io.casehub.iot.api.SwitchDevice;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceFixtureLoaderTest {

    @Test
    void loadStreamParsesMinimalDevice() {
        String yaml = """
            devices:
              - type: switch
                deviceId: sw-1
                label: Test
                on: false
            """;
        var devices = loadYaml(yaml);
        assertThat(devices).hasSize(1);
        var sw = (SwitchDevice) devices.get(0);
        assertThat(sw.deviceId()).isEqualTo("sw-1");
        assertThat(sw.isOn()).isFalse();
        assertThat(sw.tenancyId()).isEqualTo(Fixtures.DEFAULT_TENANT);
        assertThat(sw.lastUpdated()).isEqualTo(Fixtures.EPOCH);
        assertThat(sw.available()).isTrue();
    }

    @Test
    void defaultsAppliedWhenDeviceFieldsOmitted() {
        String yaml = """
            defaults:
              tenancyId: custom-tenant
              available: false
            devices:
              - type: switch
                deviceId: sw-1
                label: Test
            """;
        var devices = loadYaml(yaml);
        assertThat(devices.get(0).tenancyId()).isEqualTo("custom-tenant");
        assertThat(devices.get(0).available()).isFalse();
    }

    @Test
    void perDeviceFieldOverridesDefault() {
        String yaml = """
            defaults:
              tenancyId: default-t
            devices:
              - type: switch
                deviceId: sw-1
                label: Test
                tenancyId: override-t
            """;
        assertThat(loadYaml(yaml).get(0).tenancyId()).isEqualTo("override-t");
    }

    @Test
    void noDefaultsBlockUsesBuiltInDefaults() {
        String yaml = """
            devices:
              - type: switch
                deviceId: sw-1
                label: Test
            """;
        var device = loadYaml(yaml).get(0);
        assertThat(device.tenancyId()).isEqualTo(Fixtures.DEFAULT_TENANT);
        assertThat(device.available()).isTrue();
    }

    @Test
    void emptyDeviceListReturnsEmptyList() {
        String yaml = "devices: []\n";
        assertThat(loadYaml(yaml)).isEmpty();
    }

    @Test
    void missingDeviceIdThrows() {
        String yaml = """
            devices:
              - type: switch
                label: Test
            """;
        assertThatThrownBy(() -> loadYaml(yaml))
            .hasMessageContaining("deviceId");
    }

    @Test
    void missingLabelThrows() {
        String yaml = """
            devices:
              - type: switch
                deviceId: sw-1
            """;
        assertThatThrownBy(() -> loadYaml(yaml))
            .hasMessageContaining("label");
    }

    @Test
    void unknownTypeThrowsWithRegisteredTypes() {
        String yaml = """
            devices:
              - type: unknown
                deviceId: x
                label: X
            """;
        assertThatThrownBy(() -> loadYaml(yaml))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Unknown device type 'unknown'")
            .hasMessageContaining("switch");
    }

    @Test
    void missingTypeThrows() {
        String yaml = """
            devices:
              - deviceId: sw-1
                label: Test
            """;
        assertThatThrownBy(() -> loadYaml(yaml))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Missing required field 'type'")
            .hasMessageContaining("sw-1");
    }

    @Test
    void explicitDeviceClassThrows() {
        String yaml = """
            devices:
              - type: switch
                deviceId: sw-1
                label: Test
                deviceClass: SWITCH
            """;
        assertThatThrownBy(() -> loadYaml(yaml))
            .hasMessageContaining("deviceClass is inferred from type");
    }

    @Test
    void discoverLoadsAllCommonHandlers() {
        var registry = DeviceTypeRegistry.discover();
        assertThat(registry.handlerFor("switch")).isNotNull();
        assertThat(registry.handlerFor("light")).isNotNull();
        assertThat(registry.handlerFor("thermostat")).isNotNull();
        assertThat(registry.handlerFor("sensor")).isNotNull();
        assertThat(registry.handlerFor("presence_sensor")).isNotNull();
        assertThat(registry.handlerFor("power_sensor")).isNotNull();
        assertThat(registry.handlerFor("lock")).isNotNull();
        assertThat(registry.handlerFor("cover")).isNotNull();
        assertThat(registry.handlerFor("media_player")).isNotNull();
        assertThat(registry.handlerFor("fan")).isNotNull();
    }

    @Test
    void resourceNotFoundThrows() {
        var loader = new DeviceFixtureLoader(DeviceTypeRegistry.discover());
        assertThatThrownBy(() -> loader.loadResource("nonexistent.yaml"))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Resource not found");
    }

    private java.util.List<io.casehub.iot.api.DeviceEntity> loadYaml(String yaml) {
        var loader = new DeviceFixtureLoader(DeviceTypeRegistry.discover());
        return loader.loadStream(new ByteArrayInputStream(
            yaml.getBytes(StandardCharsets.UTF_8)));
    }
}

package io.casehub.iot.bridge.server;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.SwitchDevice;
import io.casehub.iot.api.ThermostatDevice;
import io.casehub.iot.api.bridge.DeviceIdUtils;
import io.casehub.iot.testing.Fixtures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class DeviceIdNamespacerTest {

    private static DeviceIdNamespacer namespacer;

    @BeforeAll
    static void setup() {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        namespacer = new DeviceIdNamespacer(mapper);
    }

    @Test
    void namespaceSwitchDevice() {
        SwitchDevice original = Fixtures.hallwaySwitch();

        DeviceEntity namespaced = namespacer.namespace(original, "site-a");

        assertThat(namespaced).isInstanceOf(SwitchDevice.class);
        assertThat(namespaced.deviceId()).isEqualTo("site-a/switch-hallway-1");
        assertThat(namespaced.deviceClass()).isEqualTo(DeviceClass.SWITCH);
        assertThat(namespaced.label()).isEqualTo(original.label());
        assertThat(namespaced.available()).isEqualTo(original.available());
        assertThat(namespaced.lastUpdated()).isEqualTo(original.lastUpdated());
        assertThat(namespaced.tenancyId()).isEqualTo(original.tenancyId());
        assertThat(((SwitchDevice) namespaced).isOn()).isEqualTo(original.isOn());
    }

    @Test
    void namespaceThermostatDevice() {
        // ThermostatDevice uses AbstractBuilder — no toBuilder() — proving Jackson tree copy works
        ThermostatDevice original = Fixtures.livingRoomThermostat();

        DeviceEntity namespaced = namespacer.namespace(original, "site-b");

        assertThat(namespaced).isInstanceOf(ThermostatDevice.class);
        assertThat(namespaced.deviceId()).isEqualTo("site-b/thermostat-living-1");
        assertThat(namespaced.deviceClass()).isEqualTo(DeviceClass.THERMOSTAT);
        assertThat(namespaced.label()).isEqualTo(original.label());
        assertThat(namespaced.available()).isEqualTo(original.available());

        ThermostatDevice thermostat = (ThermostatDevice) namespaced;
        assertThat(thermostat.currentTemperature()).isEqualTo(original.currentTemperature());
        assertThat(thermostat.targetTemperature()).isEqualTo(original.targetTemperature());
        assertThat(thermostat.mode()).isEqualTo(original.mode());
    }

    @Test
    void markUnavailable() {
        SwitchDevice original = Fixtures.hallwaySwitch();
        assertThat(original.available()).isTrue();

        DeviceEntity unavailable = namespacer.markUnavailable(original);

        assertThat(unavailable).isInstanceOf(SwitchDevice.class);
        assertThat(unavailable.available()).isFalse();
        assertThat(unavailable.deviceId()).isEqualTo(original.deviceId());
        assertThat(unavailable.label()).isEqualTo(original.label());
        assertThat(((SwitchDevice) unavailable).isOn()).isEqualTo(original.isOn());
    }

    @Test
    void stripPrefix() {
        assertThat(DeviceIdUtils.stripPrefix("site-a/switch.kitchen")).isEqualTo("switch.kitchen");
        assertThat(DeviceIdUtils.stripPrefix("site-a/sub/switch.kitchen")).isEqualTo("sub/switch.kitchen");
    }

    @Test
    void extractTenancyId() {
        assertThat(DeviceIdUtils.extractTenancyId("site-a/switch.kitchen")).isEqualTo("site-a");
    }

    @Test
    void noSlashReturnsOriginal() {
        assertThat(DeviceIdUtils.stripPrefix("switch.kitchen")).isEqualTo("switch.kitchen");
        assertThat(DeviceIdUtils.extractTenancyId("switch.kitchen")).isEqualTo("switch.kitchen");
    }
}

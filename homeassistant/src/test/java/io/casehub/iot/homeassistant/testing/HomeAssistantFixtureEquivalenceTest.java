package io.casehub.iot.homeassistant.testing;

import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.Temperature;
import io.casehub.iot.api.ThermostatMode;
import io.casehub.iot.homeassistant.HomeAssistantLight;
import io.casehub.iot.homeassistant.HomeAssistantLock;
import io.casehub.iot.homeassistant.HomeAssistantThermostat;
import io.casehub.iot.testing.DeviceFixtureLoader;
import io.casehub.iot.testing.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class HomeAssistantFixtureEquivalenceTest {

    @Test
    void yamlSupplementMatchesJavaConstruction() {
        var fromYaml = DeviceFixtureLoader.load("fixtures/ha-devices.yaml");
        var fromJava = List.<DeviceEntity>of(
            HomeAssistantLight.builder()
                .deviceId("ha-light-1").deviceClass(io.casehub.iot.api.DeviceClass.LIGHT)
                .label("HA RGB Light").available(true).lastUpdated(Fixtures.EPOCH)
                .tenancyId(Fixtures.DEFAULT_TENANT).providerId("test")
                .on(true).brightness(80)
                .rgbColor(new int[]{255, 100, 50}).effect("colorloop")
                .supportedColorModes(Set.of("rgb", "color_temp"))
                .build(),
            HomeAssistantThermostat.builder()
                .deviceId("ha-thermo-1").deviceClass(io.casehub.iot.api.DeviceClass.THERMOSTAT)
                .label("HA Climate").available(true).lastUpdated(Fixtures.EPOCH)
                .tenancyId(Fixtures.DEFAULT_TENANT).providerId("test")
                .currentTemperature(new Temperature(new BigDecimal("20"), Temperature.TemperatureUnit.CELSIUS))
                .targetTemperature(new Temperature(new BigDecimal("22"), Temperature.TemperatureUnit.CELSIUS))
                .mode(ThermostatMode.AUTO)
                .presetMode("comfort").swingMode("horizontal").hvacAction("heating")
                .build(),
            HomeAssistantLock.builder()
                .deviceId("ha-lock-1").deviceClass(io.casehub.iot.api.DeviceClass.LOCK)
                .label("HA Smart Lock").available(true).lastUpdated(Fixtures.EPOCH)
                .tenancyId(Fixtures.DEFAULT_TENANT).providerId("test")
                .locked(true).changedBy("user_admin").codeSlot(1)
                .build()
        );
        assertThat(fromYaml).usingRecursiveComparison()
            .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .isEqualTo(fromJava);
    }
}

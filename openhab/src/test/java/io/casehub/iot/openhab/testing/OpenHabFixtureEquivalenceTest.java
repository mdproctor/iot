package io.casehub.iot.openhab.testing;

import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.Temperature;
import io.casehub.iot.api.ThermostatMode;
import io.casehub.iot.openhab.OpenHabHsbType;
import io.casehub.iot.openhab.OpenHabLight;
import io.casehub.iot.openhab.OpenHabRollershutter;
import io.casehub.iot.openhab.OpenHabThermostat;
import io.casehub.iot.openhab.OpenHabUpDownType;
import io.casehub.iot.testing.DeviceFixtureLoader;
import io.casehub.iot.testing.Fixtures;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHabFixtureEquivalenceTest {

    @Test
    void yamlSupplementMatchesJavaConstruction() {
        var fromYaml = DeviceFixtureLoader.load("fixtures/oh-devices.yaml");
        var fromJava = List.<DeviceEntity>of(
            OpenHabLight.builder()
                .deviceId("oh-light-1").deviceClass(DeviceClass.LIGHT)
                .label("OH Color Light").available(true).lastUpdated(Fixtures.EPOCH)
                .tenancyId(Fixtures.DEFAULT_TENANT).providerId("test")
                .on(true).brightness(90)
                .hsb(new OpenHabHsbType(
                    new BigDecimal("120"), new BigDecimal("100"), new BigDecimal("90")))
                .build(),
            OpenHabThermostat.builder()
                .deviceId("oh-thermo-1").deviceClass(DeviceClass.THERMOSTAT)
                .label("OH Climate").available(true).lastUpdated(Fixtures.EPOCH)
                .tenancyId(Fixtures.DEFAULT_TENANT).providerId("test")
                .currentTemperature(new Temperature(new BigDecimal("19"), Temperature.TemperatureUnit.CELSIUS))
                .targetTemperature(new Temperature(new BigDecimal("21"), Temperature.TemperatureUnit.CELSIUS))
                .mode(ThermostatMode.HEAT)
                .heatingDemand(new BigDecimal("80")).coolingDemand(new BigDecimal("0"))
                .build(),
            OpenHabRollershutter.builder()
                .deviceId("oh-cover-1").deviceClass(DeviceClass.COVER)
                .label("OH Rollershutter").available(true).lastUpdated(Fixtures.EPOCH)
                .tenancyId(Fixtures.DEFAULT_TENANT).providerId("test")
                .position(25).moving(false).upDown(OpenHabUpDownType.STOP)
                .build()
        );
        assertThat(fromYaml).usingRecursiveComparison()
            .withComparatorForType(BigDecimal::compareTo, BigDecimal.class)
            .isEqualTo(fromJava);
    }
}

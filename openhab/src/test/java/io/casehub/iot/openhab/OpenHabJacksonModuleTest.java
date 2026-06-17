package io.casehub.iot.openhab;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.DeviceTypeIdResolver;
import io.casehub.iot.api.Temperature;
import io.casehub.iot.api.ThermostatMode;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHabJacksonModuleTest {

    private static final Instant NOW = Instant.parse("2026-06-07T10:00:00Z");

    private static JsonMapper mapper;

    @BeforeAll
    static void setupMapper() {
        mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new OpenHabJacksonModule())
                .build();
    }

    @AfterAll
    static void cleanup() {
        DeviceTypeIdResolver.deregisterType("THERMOSTAT:OpenHabThermostat");
        DeviceTypeIdResolver.deregisterType("LIGHT:OpenHabLight");
        DeviceTypeIdResolver.deregisterType("COVER:OpenHabRollershutter");
    }

    // --- OpenHabThermostat round-trip ---

    @Test
    void roundTripOpenHabThermostat() throws Exception {
        Temperature current = new Temperature(new BigDecimal("20.0"), Temperature.TemperatureUnit.CELSIUS);
        Temperature target = new Temperature(new BigDecimal("22.0"), Temperature.TemperatureUnit.CELSIUS);

        OpenHabThermostat device = OpenHabThermostat.builder()
                .deviceId("oh-th-1").deviceClass(DeviceClass.THERMOSTAT).label("OH Thermostat")
                .available(true).lastUpdated(NOW).tenancyId("t1").providerId("test")
                .currentTemperature(current).targetTemperature(target).mode(ThermostatMode.AUTO)
                .heatingDemand(new BigDecimal("75.5")).coolingDemand(new BigDecimal("0.0"))
                .build();

        String json = mapper.writeValueAsString(device);
        assertThat(json).contains("\"@deviceType\":\"THERMOSTAT:OpenHabThermostat\"");

        DeviceEntity deserialized = mapper.readValue(json, DeviceEntity.class);
        assertThat(deserialized).isInstanceOf(OpenHabThermostat.class);

        OpenHabThermostat result = (OpenHabThermostat) deserialized;

        // Common fields
        assertThat(result.deviceId()).isEqualTo("oh-th-1");
        assertThat(result.deviceClass()).isEqualTo(DeviceClass.THERMOSTAT);
        assertThat(result.label()).isEqualTo("OH Thermostat");
        assertThat(result.currentTemperature()).isEqualTo(current);
        assertThat(result.targetTemperature()).isEqualTo(target);
        assertThat(result.mode()).isEqualTo(ThermostatMode.AUTO);

        // OH-specific fields survive round-trip
        assertThat(result.heatingDemand()).hasValue(new BigDecimal("75.5"));
        assertThat(result.coolingDemand()).hasValue(new BigDecimal("0.0"));
    }

    // --- OpenHabLight round-trip ---

    @Test
    void roundTripOpenHabLight() throws Exception {
        OpenHabHsbType hsb = new OpenHabHsbType(
                new BigDecimal("240.0"),
                new BigDecimal("80.0"),
                new BigDecimal("60.0")
        );

        OpenHabLight device = OpenHabLight.builder()
                .deviceId("oh-lt-1").deviceClass(DeviceClass.LIGHT).label("OH Light")
                .available(true).lastUpdated(NOW).tenancyId("t1").providerId("test")
                .on(true).brightness(150).colorTemp(3500)
                .hsb(hsb)
                .build();

        String json = mapper.writeValueAsString(device);
        assertThat(json).contains("\"@deviceType\":\"LIGHT:OpenHabLight\"");

        DeviceEntity deserialized = mapper.readValue(json, DeviceEntity.class);
        assertThat(deserialized).isInstanceOf(OpenHabLight.class);

        OpenHabLight result = (OpenHabLight) deserialized;

        // Common fields
        assertThat(result.deviceId()).isEqualTo("oh-lt-1");
        assertThat(result.isOn()).isTrue();
        assertThat(result.brightness()).hasValue(150);

        // OH-specific field survives round-trip
        assertThat(result.hsb()).isPresent();
        OpenHabHsbType resultHsb = result.hsb().get();
        assertThat(resultHsb.hue().compareTo(new BigDecimal("240.0"))).isZero();
        assertThat(resultHsb.saturation().compareTo(new BigDecimal("80.0"))).isZero();
        assertThat(resultHsb.brightness().compareTo(new BigDecimal("60.0"))).isZero();
    }

    // --- OpenHabRollershutter round-trip ---

    @Test
    void roundTripOpenHabRollershutter() throws Exception {
        OpenHabRollershutter device = OpenHabRollershutter.builder()
                .deviceId("oh-cv-1").deviceClass(DeviceClass.COVER).label("OH Rollershutter")
                .available(true).lastUpdated(NOW).tenancyId("t1").providerId("test")
                .position(75).moving(false)
                .upDown(OpenHabUpDownType.UP)
                .build();

        String json = mapper.writeValueAsString(device);

        // Verify compound type ID format for cover
        assertThat(json).contains("\"@deviceType\":\"COVER:OpenHabRollershutter\"");

        DeviceEntity deserialized = mapper.readValue(json, DeviceEntity.class);
        assertThat(deserialized).isInstanceOf(OpenHabRollershutter.class);

        OpenHabRollershutter result = (OpenHabRollershutter) deserialized;

        // Common fields
        assertThat(result.deviceId()).isEqualTo("oh-cv-1");
        assertThat(result.position()).hasValue(75);
        assertThat(result.isMoving()).isFalse();

        // OH-specific field survives round-trip
        assertThat(result.upDown()).hasValue(OpenHabUpDownType.UP);
    }

    // --- Module registers all 3 types ---

    @Test
    void moduleRegistersAllThreeTypes() {
        assertThat(DeviceTypeIdResolver.isRegistered("THERMOSTAT:OpenHabThermostat")).isTrue();
        assertThat(DeviceTypeIdResolver.isRegistered("LIGHT:OpenHabLight")).isTrue();
        assertThat(DeviceTypeIdResolver.isRegistered("COVER:OpenHabRollershutter")).isTrue();
    }
}

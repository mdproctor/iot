package io.casehub.iot.homeassistant;

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

class HomeAssistantJacksonModuleTest {

    private static final Instant NOW = Instant.parse("2026-06-07T10:00:00Z");

    private static JsonMapper mapper;

    @BeforeAll
    static void setupMapper() {
        mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .addModule(new HomeAssistantJacksonModule())
                .build();
    }

    @AfterAll
    static void cleanup() {
        // Deregister types to avoid polluting other test classes in the same JVM
        DeviceTypeIdResolver.deregisterType("THERMOSTAT:HomeAssistantThermostat");
        DeviceTypeIdResolver.deregisterType("LIGHT:HomeAssistantLight");
        DeviceTypeIdResolver.deregisterType("LOCK:HomeAssistantLock");
    }

    // --- HomeAssistantThermostat round-trip ---

    @Test
    void roundTripHomeAssistantThermostat() throws Exception {
        Temperature current = new Temperature(new BigDecimal("21.5"), Temperature.TemperatureUnit.CELSIUS);
        Temperature target = new Temperature(new BigDecimal("23.0"), Temperature.TemperatureUnit.CELSIUS);

        HomeAssistantThermostat device = HomeAssistantThermostat.builder()
                .deviceId("ha-th-1").deviceClass(DeviceClass.THERMOSTAT).label("HA Thermostat")
                .available(true).lastUpdated(NOW).tenancyId("t1").providerId("test")
                .currentTemperature(current).targetTemperature(target).mode(ThermostatMode.HEAT)
                .presetMode("comfort").hvacAction("heating").swingMode("vertical")
                .build();

        String json = mapper.writeValueAsString(device);

        // Verify compound type ID format
        assertThat(json).contains("\"@deviceType\":\"THERMOSTAT:HomeAssistantThermostat\"");

        DeviceEntity deserialized = mapper.readValue(json, DeviceEntity.class);

        // Verify instanceof — must be the supplement type, not the common parent
        assertThat(deserialized).isInstanceOf(HomeAssistantThermostat.class);

        HomeAssistantThermostat result = (HomeAssistantThermostat) deserialized;

        // Common fields survive round-trip
        assertThat(result.deviceId()).isEqualTo("ha-th-1");
        assertThat(result.deviceClass()).isEqualTo(DeviceClass.THERMOSTAT);
        assertThat(result.label()).isEqualTo("HA Thermostat");
        assertThat(result.available()).isTrue();
        assertThat(result.lastUpdated()).isEqualTo(NOW);
        assertThat(result.tenancyId()).isEqualTo("t1");
        assertThat(result.currentTemperature()).isEqualTo(current);
        assertThat(result.targetTemperature()).isEqualTo(target);
        assertThat(result.mode()).isEqualTo(ThermostatMode.HEAT);

        // HA-specific fields survive round-trip
        assertThat(result.presetMode()).hasValue("comfort");
        assertThat(result.hvacAction()).hasValue("heating");
        assertThat(result.swingMode()).hasValue("vertical");
    }

    // --- HomeAssistantLight round-trip ---

    @Test
    void roundTripHomeAssistantLight() throws Exception {
        HomeAssistantLight device = HomeAssistantLight.builder()
                .deviceId("ha-lt-1").deviceClass(DeviceClass.LIGHT).label("HA Light")
                .available(true).lastUpdated(NOW).tenancyId("t1").providerId("test")
                .on(true).brightness(200).colorTemp(4000)
                .rgbColor(new int[]{255, 128, 0}).effect("rainbow")
                .build();

        String json = mapper.writeValueAsString(device);
        assertThat(json).contains("\"@deviceType\":\"LIGHT:HomeAssistantLight\"");

        DeviceEntity deserialized = mapper.readValue(json, DeviceEntity.class);
        assertThat(deserialized).isInstanceOf(HomeAssistantLight.class);

        HomeAssistantLight result = (HomeAssistantLight) deserialized;

        // Common fields
        assertThat(result.deviceId()).isEqualTo("ha-lt-1");
        assertThat(result.isOn()).isTrue();
        assertThat(result.brightness()).hasValue(200);
        assertThat(result.colorTemp()).hasValue(4000);

        // HA-specific fields survive round-trip
        assertThat(result.rgbColor()).isPresent();
        assertThat(result.rgbColor().get()).containsExactly(255, 128, 0);
        assertThat(result.effect()).hasValue("rainbow");
    }

    // --- HomeAssistantLock round-trip ---

    @Test
    void roundTripHomeAssistantLock() throws Exception {
        HomeAssistantLock device = HomeAssistantLock.builder()
                .deviceId("ha-lk-1").deviceClass(DeviceClass.LOCK).label("HA Lock")
                .available(true).lastUpdated(NOW).tenancyId("t1").providerId("test")
                .locked(true)
                .changedBy("john_doe").codeSlot(3)
                .build();

        String json = mapper.writeValueAsString(device);
        assertThat(json).contains("\"@deviceType\":\"LOCK:HomeAssistantLock\"");

        DeviceEntity deserialized = mapper.readValue(json, DeviceEntity.class);
        assertThat(deserialized).isInstanceOf(HomeAssistantLock.class);

        HomeAssistantLock result = (HomeAssistantLock) deserialized;

        // Common fields
        assertThat(result.deviceId()).isEqualTo("ha-lk-1");
        assertThat(result.isLocked()).isTrue();

        // HA-specific fields survive round-trip
        assertThat(result.changedBy()).hasValue("john_doe");
        assertThat(result.codeSlot()).hasValue(3);
    }

    // --- Module registers all 3 types ---

    @Test
    void moduleRegistersAllThreeTypes() {
        assertThat(DeviceTypeIdResolver.isRegistered("THERMOSTAT:HomeAssistantThermostat")).isTrue();
        assertThat(DeviceTypeIdResolver.isRegistered("LIGHT:HomeAssistantLight")).isTrue();
        assertThat(DeviceTypeIdResolver.isRegistered("LOCK:HomeAssistantLock")).isTrue();
    }
}

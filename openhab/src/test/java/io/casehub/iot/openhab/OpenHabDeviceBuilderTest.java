package io.casehub.iot.openhab;

import io.casehub.iot.api.*;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHabDeviceBuilderTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final String TENANT = "test-tenant";

    // ---- helpers ----

    private ResolvedDeviceFields.Builder base(String id, DeviceClass dc) {
        return ResolvedDeviceFields.builder()
                .deviceId(id)
                .label(id)
                .available(true)
                .now(NOW)
                .tenancyId(TENANT)
                .deviceClass(dc);
    }

    // ---- 1. THERMOSTAT without demand → ThermostatDevice (not OpenHabThermostat) ----

    @Test
    void thermostatWithoutDemandBuildsThermostatDevice() {
        var fields = base("therm1", DeviceClass.THERMOSTAT)
                .currentTemperature(new Temperature(new BigDecimal("21.5"), Temperature.TemperatureUnit.CELSIUS))
                .targetTemperature(new Temperature(new BigDecimal("22.0"), Temperature.TemperatureUnit.CELSIUS))
                .mode(ThermostatMode.HEAT)
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(ThermostatDevice.class)
                .isNotInstanceOf(OpenHabThermostat.class);
        var therm = (ThermostatDevice) result;
        assertThat(therm.deviceId()).isEqualTo("therm1");
        assertThat(therm.currentTemperature().value()).isEqualByComparingTo("21.5");
        assertThat(therm.targetTemperature().value()).isEqualByComparingTo("22.0");
        assertThat(therm.mode()).isEqualTo(ThermostatMode.HEAT);
    }

    // ---- 2. THERMOSTAT with heatingDemand → OpenHabThermostat ----

    @Test
    void thermostatWithHeatingDemandBuildsOpenHabThermostat() {
        var fields = base("therm2", DeviceClass.THERMOSTAT)
                .currentTemperature(new Temperature(new BigDecimal("19.0"), Temperature.TemperatureUnit.CELSIUS))
                .targetTemperature(new Temperature(new BigDecimal("21.0"), Temperature.TemperatureUnit.CELSIUS))
                .mode(ThermostatMode.HEAT)
                .heatingDemand(new BigDecimal("75"))
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(OpenHabThermostat.class);
        var therm = (OpenHabThermostat) result;
        assertThat(therm.heatingDemand()).hasValue(new BigDecimal("75"));
        assertThat(therm.coolingDemand()).isEmpty();
    }

    // ---- 3. LIGHT with HSB → OpenHabLight ----

    @Test
    void lightWithHsbBuildsOpenHabLight() {
        var hsb = new OpenHabHsbType(new BigDecimal("240"), new BigDecimal("100"), new BigDecimal("50"));
        var fields = base("light1", DeviceClass.LIGHT)
                .on(true)
                .brightness(50)
                .hsb(hsb)
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(OpenHabLight.class);
        var light = (OpenHabLight) result;
        assertThat(light.hsb()).isPresent();
        assertThat(light.hsb().get().hue()).isEqualByComparingTo("240");
        assertThat(light.isOn()).isTrue();
        assertThat(light.brightness()).hasValue(50);
    }

    // ---- 4. LIGHT without HSB → LightDevice (not OpenHabLight) ----

    @Test
    void lightWithoutHsbBuildsLightDevice() {
        var fields = base("light2", DeviceClass.LIGHT)
                .on(true)
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(LightDevice.class)
                .isNotInstanceOf(OpenHabLight.class);
        var light = (LightDevice) result;
        assertThat(light.isOn()).isTrue();
    }

    // ---- 5. SWITCH → SwitchDevice ----

    @Test
    void switchBuildsSwitchDevice() {
        var fields = base("switch1", DeviceClass.SWITCH)
                .on(true)
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(SwitchDevice.class);
        var sw = (SwitchDevice) result;
        assertThat(sw.isOn()).isTrue();
        assertThat(sw.deviceClass()).isEqualTo(DeviceClass.SWITCH);
    }

    // ---- 6. COVER with isRollershutter → OpenHabRollershutter ----

    @Test
    void coverWithRollershutterBuildsOpenHabRollershutter() {
        var fields = base("cover1", DeviceClass.COVER)
                .position(70)
                .isRollershutter(true)
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(OpenHabRollershutter.class);
        var cover = (OpenHabRollershutter) result;
        assertThat(cover.position()).hasValue(70);
    }

    // ---- 7. COVER without isRollershutter → CoverDevice ----

    @Test
    void coverWithoutRollershutterBuildsCoverDevice() {
        var fields = base("cover2", DeviceClass.COVER)
                .position(50)
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(CoverDevice.class)
                .isNotInstanceOf(OpenHabRollershutter.class);
        var cover = (CoverDevice) result;
        assertThat(cover.position()).hasValue(50);
    }

    // ---- 8. POWER_SENSOR → PowerSensor ----

    @Test
    void powerSensorBuildsPowerSensor() {
        var fields = base("power1", DeviceClass.POWER_SENSOR)
                .power(new BigDecimal("1500"))
                .energy(new BigDecimal("42.7"))
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(PowerSensor.class);
        var ps = (PowerSensor) result;
        assertThat(ps.power()).hasValue(new BigDecimal("1500"));
        assertThat(ps.energy()).hasValue(new BigDecimal("42.7"));
        assertThat(ps.deviceClass()).isEqualTo(DeviceClass.POWER_SENSOR);
    }

    // ---- 9. PRESENCE_SENSOR → PresenceSensor ----

    @Test
    void presenceSensorBuildsPresenceSensor() {
        var fields = base("presence1", DeviceClass.PRESENCE_SENSOR)
                .present(true)
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(PresenceSensor.class);
        var ps = (PresenceSensor) result;
        assertThat(ps.isPresent()).isTrue();
        assertThat(ps.lastSeen()).isEqualTo(NOW);
        assertThat(ps.deviceClass()).isEqualTo(DeviceClass.PRESENCE_SENSOR);
    }

    // ---- 10. SENSOR → SensorDevice ----

    @Test
    void sensorBuildsSensorDevice() {
        var fields = base("sensor1", DeviceClass.SENSOR)
                .sensorType(SensorType.HUMIDITY)
                .numericValue(new BigDecimal("68.5"))
                .unit("%")
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(SensorDevice.class);
        var sensor = (SensorDevice) result;
        assertThat(sensor.sensorType()).isEqualTo(SensorType.HUMIDITY);
        assertThat(sensor.numericValue()).hasValue(new BigDecimal("68.5"));
        assertThat(sensor.unit()).hasValue("%");
    }

    // ---- 11. FAN → FanDevice ----

    @Test
    void fanBuildsFanDevice() {
        var fields = base("fan1", DeviceClass.FAN)
                .on(true)
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(FanDevice.class);
        var fan = (FanDevice) result;
        assertThat(fan.isOn()).isTrue();
        assertThat(fan.deviceClass()).isEqualTo(DeviceClass.FAN);
    }

    // ---- 12. LOCK → LockDevice ----

    @Test
    void lockBuildsLockDevice() {
        var fields = base("lock1", DeviceClass.LOCK)
                .locked(true)
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(LockDevice.class);
        var lock = (LockDevice) result;
        assertThat(lock.isLocked()).isTrue();
        assertThat(lock.deviceClass()).isEqualTo(DeviceClass.LOCK);
    }

    // ---- 13. MEDIA_PLAYER → MediaPlayerDevice ----

    @Test
    void mediaPlayerBuildsMediaPlayerDevice() {
        var fields = base("media1", DeviceClass.MEDIA_PLAYER)
                .volume(65)
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(MediaPlayerDevice.class);
        var mp = (MediaPlayerDevice) result;
        assertThat(mp.volume()).hasValue(65);
        assertThat(mp.deviceClass()).isEqualTo(DeviceClass.MEDIA_PLAYER);
    }

    // ---- 14. Null temperature defaults to (0, CELSIUS) ----

    @Test
    void nullTemperatureDefaultsToZeroCelsius() {
        var fields = base("therm3", DeviceClass.THERMOSTAT).build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result).isInstanceOf(ThermostatDevice.class);
        var therm = (ThermostatDevice) result;
        assertThat(therm.currentTemperature().value()).isEqualByComparingTo("0");
        assertThat(therm.currentTemperature().unit()).isEqualTo(Temperature.TemperatureUnit.CELSIUS);
        assertThat(therm.targetTemperature().value()).isEqualByComparingTo("0");
        assertThat(therm.targetTemperature().unit()).isEqualTo(Temperature.TemperatureUnit.CELSIUS);
    }

    // ---- 15. Null mode defaults to OFF ----

    @Test
    void nullModeDefaultsToOff() {
        var fields = base("therm4", DeviceClass.THERMOSTAT)
                .currentTemperature(new Temperature(BigDecimal.ONE, Temperature.TemperatureUnit.CELSIUS))
                .targetTemperature(new Temperature(BigDecimal.TEN, Temperature.TemperatureUnit.CELSIUS))
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        var therm = (ThermostatDevice) result;
        assertThat(therm.mode()).isEqualTo(ThermostatMode.OFF);
    }

    // ---- 16. Null boolean wrappers default to false ----

    @Test
    void nullBooleanWrappersDefaultToFalse() {
        // on=null for switch → false
        var fields = base("switch2", DeviceClass.SWITCH).build();

        var result = OpenHabDeviceBuilder.build(fields);

        var sw = (SwitchDevice) result;
        assertThat(sw.isOn()).isFalse();
    }

    // ---- 17. Common device fields are carried through ----

    @Test
    void commonDeviceFieldsCarriedThrough() {
        var fields = ResolvedDeviceFields.builder()
                .deviceId("my-device")
                .label("My Device Label")
                .available(false)
                .now(NOW)
                .tenancyId("tenant-xyz")
                .deviceClass(DeviceClass.SWITCH)
                .on(true)
                .build();

        var result = OpenHabDeviceBuilder.build(fields);

        assertThat(result.deviceId()).isEqualTo("my-device");
        assertThat(result.label()).isEqualTo("My Device Label");
        assertThat(result.available()).isFalse();
        assertThat(result.lastUpdated()).isEqualTo(NOW);
        assertThat(result.tenancyId()).isEqualTo("tenant-xyz");
    }
}

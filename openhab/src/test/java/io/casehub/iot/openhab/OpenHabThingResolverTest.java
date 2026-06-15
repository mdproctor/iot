package io.casehub.iot.openhab;

import io.casehub.iot.api.*;
import io.casehub.iot.openhab.internal.OpenHabChannelDto;
import io.casehub.iot.openhab.internal.OpenHabItemDto;
import io.casehub.iot.openhab.internal.OpenHabStatusInfoDto;
import io.casehub.iot.openhab.internal.OpenHabThingDto;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHabThingResolverTest {

    private static final Instant NOW = Instant.parse("2026-06-15T12:00:00Z");
    private static final String TENANT = "test-tenant";

    private final OpenHabThingResolver resolver = new OpenHabThingResolver(TENANT);

    // ---- helper methods ----

    private OpenHabThingDto thing(String uid, String label, String status, OpenHabChannelDto... channels) {
        return new OpenHabThingDto(uid, label, "binding:type",
                new OpenHabStatusInfoDto(status, "NONE"), List.of(channels), null);
    }

    private OpenHabChannelDto channel(String id, String itemType, String... linkedItems) {
        return new OpenHabChannelDto("thing:" + id, id, "system:" + id,
                itemType, "STATE", List.of(linkedItems), List.of());
    }

    private OpenHabChannelDto triggerChannel(String id, String itemType, String... linkedItems) {
        return new OpenHabChannelDto("thing:" + id, id, "system:" + id,
                itemType, "TRIGGER", List.of(linkedItems), List.of());
    }

    private OpenHabChannelDto channelWithType(String id, String itemType, String channelTypeUID, String... linkedItems) {
        return new OpenHabChannelDto("thing:" + id, id, channelTypeUID,
                itemType, "STATE", List.of(linkedItems), List.of());
    }

    private Map<String, OpenHabItemDto> items(String... pairs) {
        Map<String, OpenHabItemDto> map = new HashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            map.put(pairs[i], new OpenHabItemDto(null, pairs[i], pairs[i], pairs[i + 1], null, null, null));
        }
        return map;
    }

    // ---- 1. Color channel → LIGHT, HSB populated, on=true when brightness > 0 ----

    @Test
    void colorChannelMapsToLightWithHsb() {
        var t = thing("thing:color1", "Color Light", "ONLINE",
                channel("color", "Color", "colorItem"));
        var itemStates = items("colorItem", "240,100,50");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.LIGHT);
        assertThat(fields.hsb()).isNotNull();
        assertThat(fields.hsb().hue()).isEqualByComparingTo("240");
        assertThat(fields.hsb().saturation()).isEqualByComparingTo("100");
        assertThat(fields.hsb().brightness()).isEqualByComparingTo("50");
        assertThat(fields.brightness()).isEqualTo(50);
        assertThat(fields.on()).isTrue();
    }

    // ---- 2. Dimmer channel (no Color) → LIGHT with brightness ----

    @Test
    void dimmerChannelMapsToLightWithBrightness() {
        var t = thing("thing:dimmer1", "Dimmer Light", "ONLINE",
                channel("brightness", "Dimmer", "dimmerItem"));
        var itemStates = items("dimmerItem", "75");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.LIGHT);
        assertThat(fields.brightness()).isEqualTo(75);
        assertThat(fields.on()).isTrue();
    }

    // ---- 3. Switch-only → SWITCH, on=true when "ON" ----

    @Test
    void switchOnlyMapsToSwitch() {
        var t = thing("thing:switch1", "Wall Switch", "ONLINE",
                channel("power", "Switch", "switchItem"));
        var itemStates = items("switchItem", "ON");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.SWITCH);
        assertThat(fields.on()).isTrue();
    }

    // ---- 4. Rollershutter → COVER, position inverted (OH 30 → CaseHub 70) ----

    @Test
    void rollershutterMapsToCoverWithInvertedPosition() {
        var t = thing("thing:blinds1", "Bedroom Blinds", "ONLINE",
                channel("position", "Rollershutter", "blindsItem"));
        var itemStates = items("blindsItem", "30");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.COVER);
        assertThat(fields.position()).isEqualTo(70);
        assertThat(fields.isRollershutter()).isTrue();
    }

    // ---- 5. Player → MEDIA_PLAYER ----

    @Test
    void playerChannelMapsToMediaPlayer() {
        var t = thing("thing:player1", "Media Player", "ONLINE",
                channel("control", "Player", "playerItem"));
        var itemStates = items("playerItem", "PLAY");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.MEDIA_PLAYER);
    }

    // ---- 6. Number:Power → POWER_SENSOR ----

    @Test
    void numberPowerMapsToPowerSensor() {
        var t = thing("thing:power1", "Power Meter", "ONLINE",
                channel("power", "Number:Power", "powerItem"));
        var itemStates = items("powerItem", "1500");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.POWER_SENSOR);
        assertThat(fields.power()).isEqualByComparingTo("1500");
    }

    // ---- 7. Number:Energy → POWER_SENSOR with energy ----

    @Test
    void numberEnergyMapsToPowerSensorWithEnergy() {
        var t = thing("thing:energy1", "Energy Meter", "ONLINE",
                channel("energy", "Number:Energy", "energyItem"));
        var itemStates = items("energyItem", "42.7");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.POWER_SENSOR);
        assertThat(fields.energy()).isEqualByComparingTo("42.7");
    }

    // ---- 8. Dual Number:Temperature with setpoint channelTypeUID → THERMOSTAT ----

    @Test
    void dualTemperatureWithSetpointMapsThermostat() {
        var t = thing("thing:therm1", "Thermostat", "ONLINE",
                channel("temperature", "Number:Temperature", "tempItem"),
                channelWithType("setpoint", "Number:Temperature",
                        "hvac:setpoint-temperature", "setpointItem"));
        var itemStates = items("tempItem", "21.5", "setpointItem", "22.0");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.THERMOSTAT);
        assertThat(fields.currentTemperature().value()).isEqualByComparingTo("21.5");
        assertThat(fields.targetTemperature().value()).isEqualByComparingTo("22.0");
    }

    // ---- 9. Single Number:Temperature → SENSOR with SensorType.TEMPERATURE ----

    @Test
    void singleTemperatureMapsToSensor() {
        var t = thing("thing:sensor1", "Temp Sensor", "ONLINE",
                channel("temperature", "Number:Temperature", "tempItem"));
        var itemStates = items("tempItem", "18.3");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.SENSOR);
        assertThat(fields.sensorType()).isEqualTo(SensorType.TEMPERATURE);
        assertThat(fields.numericValue()).isEqualByComparingTo("18.3");
    }

    // ---- 10. Number:Humidity → SENSOR with SensorType.HUMIDITY ----

    @Test
    void numberHumidityMapsToSensorHumidity() {
        var t = thing("thing:humidity1", "Humidity Sensor", "ONLINE",
                channel("humidity", "Number:Humidity", "humidityItem"));
        var itemStates = items("humidityItem", "68.5");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.SENSOR);
        assertThat(fields.sensorType()).isEqualTo(SensorType.HUMIDITY);
        assertThat(fields.numericValue()).isEqualByComparingTo("68.5");
    }

    // ---- 11. Contact → SENSOR with SensorType.GENERIC ----

    @Test
    void contactMapsToSensorGeneric() {
        var t = thing("thing:contact1", "Door Sensor", "ONLINE",
                channel("state", "Contact", "contactItem"));
        var itemStates = items("contactItem", "OPEN");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.SENSOR);
        assertThat(fields.sensorType()).isEqualTo(SensorType.GENERIC);
    }

    // ---- 12. Bare Number → SENSOR with SensorType.GENERIC ----

    @Test
    void bareNumberMapsToSensorGeneric() {
        var t = thing("thing:number1", "Generic Number", "ONLINE",
                channel("value", "Number", "numberItem"));
        var itemStates = items("numberItem", "42");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.SENSOR);
        assertThat(fields.sensorType()).isEqualTo(SensorType.GENERIC);
        assertThat(fields.numericValue()).isEqualByComparingTo("42");
    }

    // ---- 13. OFFLINE Thing → available=false ----

    @Test
    void offlineThingMarksUnavailable() {
        var t = thing("thing:offline1", "Offline Switch", "OFFLINE",
                channel("power", "Switch", "switchItem"));
        var itemStates = items("switchItem", "ON");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.available()).isFalse();
    }

    // ---- 14. No mappable STATE channels → null ----

    @Test
    void noMappableChannelsReturnsNull() {
        var t = thing("thing:empty1", "Empty Thing", "ONLINE");
        Map<String, OpenHabItemDto> itemStates = Map.of();

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNull();
    }

    // ---- 15. TRIGGER channels excluded from inference ----

    @Test
    void triggerChannelsExcludedFromInference() {
        var t = thing("thing:trigger1", "Trigger Only", "ONLINE",
                triggerChannel("button", "String", "buttonItem"));
        var itemStates = items("buttonItem", "PRESSED");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNull();
    }

    // ---- 16. Priority: Switch + bare Number → SWITCH (priority 9 beats 11) ----

    @Test
    void prioritySwitchBeatsNumber() {
        var t = thing("thing:priority1", "Switch + Number", "ONLINE",
                channel("value", "Number", "numberItem"),
                channel("power", "Switch", "switchItem"));
        var itemStates = items("numberItem", "42", "switchItem", "ON");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.SWITCH);
        assertThat(fields.on()).isTrue();
    }

    // ---- 17. Priority: Color + Switch → LIGHT (priority 1 beats 9) ----

    @Test
    void priorityColorBeatsSwitch() {
        var t = thing("thing:priority2", "Color + Switch", "ONLINE",
                channel("power", "Switch", "switchItem"),
                channel("color", "Color", "colorItem"));
        var itemStates = items("switchItem", "ON", "colorItem", "240,100,50");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.LIGHT);
        assertThat(fields.hsb()).isNotNull();
    }

    // ---- 18. Thermostat mode from String channel with "mode" in id ----

    @Test
    void thermostatModeFromStringChannel() {
        var t = thing("thing:therm2", "Mode Thermostat", "ONLINE",
                channel("temperature", "Number:Temperature", "tempItem"),
                channelWithType("setpoint", "Number:Temperature",
                        "hvac:setpoint-temperature", "setpointItem"),
                channel("hvac_mode", "String", "modeItem"));
        var itemStates = items("tempItem", "20.0", "setpointItem", "21.0",
                "modeItem", "heat");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.THERMOSTAT);
        assertThat(fields.mode()).isEqualTo(ThermostatMode.HEAT);
    }

    // ---- 19. Thermostat mode defaults to OFF when no mode channel ----

    @Test
    void thermostatModeDefaultsToOff() {
        var t = thing("thing:therm3", "No Mode Thermostat", "ONLINE",
                channel("temperature", "Number:Temperature", "tempItem"),
                channelWithType("setpoint", "Number:Temperature",
                        "hvac:setpoint-temperature", "setpointItem"));
        var itemStates = items("tempItem", "20.0", "setpointItem", "21.0");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.THERMOSTAT);
        assertThat(fields.mode()).isEqualTo(ThermostatMode.OFF);
    }

    // ---- 20. Switch OFF on thermostat → mode OFF ----

    @Test
    void thermostatSwitchOffOverridesMode() {
        var t = thing("thing:therm4", "Switch Off Thermostat", "ONLINE",
                channel("temperature", "Number:Temperature", "tempItem"),
                channelWithType("setpoint", "Number:Temperature",
                        "hvac:setpoint-temperature", "setpointItem"),
                channel("hvac_mode", "String", "modeItem"),
                channel("power", "Switch", "switchItem"));
        var itemStates = items("tempItem", "20.0", "setpointItem", "21.0",
                "modeItem", "heat", "switchItem", "OFF");

        var fields = resolver.resolve(t, itemStates, NOW);

        assertThat(fields).isNotNull();
        assertThat(fields.deviceClass()).isEqualTo(DeviceClass.THERMOSTAT);
        assertThat(fields.mode()).isEqualTo(ThermostatMode.OFF);
    }
}

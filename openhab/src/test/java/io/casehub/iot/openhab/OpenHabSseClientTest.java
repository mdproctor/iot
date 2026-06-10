package io.casehub.iot.openhab;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.iot.api.*;
import io.casehub.iot.openhab.internal.OpenHabItemDto;
import io.casehub.iot.openhab.internal.OpenHabSseEventDto;
import io.casehub.iot.openhab.internal.OpenHabStatePayloadDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OpenHabSseClient} cache management and item resolution logic.
 *
 * <p>These tests exercise the deterministic (non-CDI) parts of the SSE client:
 * cache population, item-to-Equipment resolution, SSE event handling, and
 * target item resolution for command dispatch. Coalescing timer behaviour
 * requires CDI event infrastructure and is verified in integration tests.</p>
 */
class OpenHabSseClientTest {

    private static final Instant NOW = Instant.parse("2026-06-10T12:00:00Z");

    private OpenHabSseClient sseClient;
    private OpenHabEntityMapper mapper;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        mapper = new OpenHabEntityMapper("test-tenant");
        objectMapper = new ObjectMapper();
        sseClient = new OpenHabSseClient(mapper, objectMapper);
    }

    // ---- helper methods ----

    private OpenHabItemDto equipment(String name, String label, List<String> tags, OpenHabItemDto... members) {
        return new OpenHabItemDto("Group", name, label, "NULL", tags, List.of(members), null);
    }

    private OpenHabItemDto member(String type, String name, String state, List<String> tags) {
        return new OpenHabItemDto(type, name, name, state, tags, null, null);
    }

    private List<OpenHabItemDto> twoEquipments() {
        return List.of(
            equipment("SwitchLiving", "Living Room Switch",
                List.of("Equipment", "PowerOutlet"),
                member("Switch", "SwitchLiving_Toggle", "ON",
                    List.of("Point", "Control", "Switch"))),
            equipment("ThermostatHall", "Hall Thermostat",
                List.of("Equipment", "HVAC"),
                member("Number", "ThermostatHall_CurrentTemp", "20.0",
                    List.of("Point", "Measurement", "Temperature")),
                member("Number", "ThermostatHall_TargetTemp", "21.0",
                    List.of("Point", "Setpoint", "Temperature")),
                member("String", "ThermostatHall_Mode", "heat",
                    List.of()))
        );
    }

    private String sseEventJson(String topic, String newValue) throws Exception {
        var payload = new OpenHabStatePayloadDto("Decimal", newValue, "Decimal", "20.0");
        var payloadJson = objectMapper.writeValueAsString(payload);
        var event = new OpenHabSseEventDto(topic, payloadJson, "ItemStateChangedEvent");
        return objectMapper.writeValueAsString(event);
    }

    // ---- 1. populateCaches populates all indexes ----

    @Test
    void populatesCachesFromDiscoveryResponse() {
        var equipments = twoEquipments();

        sseClient.populateCaches(equipments);

        // itemToEquipment reverse index
        assertThat(sseClient.getItemToEquipment("SwitchLiving_Toggle"))
            .isEqualTo("SwitchLiving");
        assertThat(sseClient.getItemToEquipment("ThermostatHall_CurrentTemp"))
            .isEqualTo("ThermostatHall");
        assertThat(sseClient.getItemToEquipment("ThermostatHall_TargetTemp"))
            .isEqualTo("ThermostatHall");
        assertThat(sseClient.getItemToEquipment("ThermostatHall_Mode"))
            .isEqualTo("ThermostatHall");

        // deviceCache populated
        var devices = sseClient.cachedDevices();
        assertThat(devices).hasSize(2);
        assertThat(devices).extracting(DeviceEntity::deviceId)
            .containsExactlyInAnyOrder("SwitchLiving", "ThermostatHall");

        // itemStateCache populated (accessible via equipment reconstruction)
        var switchDevice = devices.stream()
            .filter(d -> d.deviceId().equals("SwitchLiving")).findFirst().orElseThrow();
        assertThat(switchDevice).isInstanceOf(SwitchDevice.class);
        assertThat(((SwitchDevice) switchDevice).isOn()).isTrue();
    }

    // ---- 2. resolveTargetItem finds Control+Switch for turn_on ----

    @Test
    void resolveTargetItemFindsControlSwitchForTurnOn() {
        sseClient.populateCaches(List.of(
            equipment("SwitchLiving", "Living Room Switch",
                List.of("Equipment", "PowerOutlet"),
                member("Switch", "SwitchLiving_Toggle", "ON",
                    List.of("Point", "Control", "Switch")))));

        var command = DeviceCommand.turnOn("SwitchLiving", Map.of(), "test", "corr-1");
        String targetItem = sseClient.resolveTargetItem(command);

        assertThat(targetItem).isEqualTo("SwitchLiving_Toggle");
    }

    // ---- 3. resolveTargetItem finds Setpoint+Temperature for set_temperature ----

    @Test
    void resolveTargetItemFindsSetpointForSetTemperature() {
        sseClient.populateCaches(List.of(
            equipment("ThermostatHall", "Hall Thermostat",
                List.of("Equipment", "HVAC"),
                member("Number", "ThermostatHall_CurrentTemp", "20.0",
                    List.of("Point", "Measurement", "Temperature")),
                member("Number", "ThermostatHall_TargetTemp", "21.0",
                    List.of("Point", "Setpoint", "Temperature")),
                member("String", "ThermostatHall_Mode", "heat",
                    List.of()))));

        var command = DeviceCommand.setTemperature("ThermostatHall",
            new Temperature(new java.math.BigDecimal("22.0"), Temperature.TemperatureUnit.CELSIUS),
            "test", "corr-2");
        String targetItem = sseClient.resolveTargetItem(command);

        assertThat(targetItem).isEqualTo("ThermostatHall_TargetTemp");
    }

    // ---- 4. resolveTargetItem returns null for unknown device ----

    @Test
    void resolveTargetItemReturnsNullForUnknownDevice() {
        sseClient.populateCaches(twoEquipments());

        var command = DeviceCommand.turnOn("NonExistentDevice", Map.of(), "test", "corr-3");
        String targetItem = sseClient.resolveTargetItem(command);

        assertThat(targetItem).isNull();
    }

    // ---- 5. SSE event for unknown item is ignored ----

    @Test
    void itemNotInEquipmentIsIgnored() throws Exception {
        sseClient.populateCaches(twoEquipments());
        var devicesBefore = sseClient.cachedDevices();

        // Event for an item not in any Equipment
        String eventData = sseEventJson("openhab/items/UnknownItem/statechanged", "42");
        sseClient.handleSseEvent(eventData);

        // Cache unchanged
        assertThat(sseClient.cachedDevices()).containsExactlyInAnyOrderElementsOf(devicesBefore);
    }

    // ---- 6. handleSseEvent updates device cache ----

    @Test
    void handleSseEventUpdatesDeviceCache() throws Exception {
        sseClient.populateCaches(List.of(
            equipment("SwitchLiving", "Living Room Switch",
                List.of("Equipment", "PowerOutlet"),
                member("Switch", "SwitchLiving_Toggle", "ON",
                    List.of("Point", "Control", "Switch")))));

        // Verify initial state
        var deviceBefore = sseClient.cachedDevices().stream()
            .filter(d -> d.deviceId().equals("SwitchLiving")).findFirst().orElseThrow();
        assertThat(((SwitchDevice) deviceBefore).isOn()).isTrue();

        // Simulate state change: switch turned OFF
        var payload = new OpenHabStatePayloadDto("OnOff", "OFF", "OnOff", "ON");
        var payloadJson = objectMapper.writeValueAsString(payload);
        var event = new OpenHabSseEventDto(
            "openhab/items/SwitchLiving_Toggle/statechanged", payloadJson, "ItemStateChangedEvent");
        String eventData = objectMapper.writeValueAsString(event);

        sseClient.handleSseEvent(eventData);

        // Verify cache updated
        var deviceAfter = sseClient.cachedDevices().stream()
            .filter(d -> d.deviceId().equals("SwitchLiving")).findFirst().orElseThrow();
        assertThat(((SwitchDevice) deviceAfter).isOn()).isFalse();
    }

    // ---- 7. resolveTargetItem finds Control+Switch for lock ----

    @Test
    void resolveTargetItemFindsControlSwitchForLock() {
        sseClient.populateCaches(List.of(
            equipment("LockFront", "Front Door Lock",
                List.of("Equipment", "Lock"),
                member("Switch", "LockFront_Switch", "ON",
                    List.of("Point", "Control", "Switch")))));

        var command = DeviceCommand.lock("LockFront", "test", "corr-4");
        String targetItem = sseClient.resolveTargetItem(command);

        assertThat(targetItem).isEqualTo("LockFront_Switch");
    }

    // ---- 8. resolveTargetItem finds Status+OpenState for set_position ----

    @Test
    void resolveTargetItemFindsOpenStateForSetPosition() {
        sseClient.populateCaches(List.of(
            equipment("BlindsBedroom", "Bedroom Blinds",
                List.of("Equipment", "Blinds"),
                member("Rollershutter", "BlindsBedroom_Position", "30",
                    List.of("Point", "Status", "OpenState")))));

        var command = DeviceCommand.setPosition("BlindsBedroom", 70, "test", "corr-5");
        String targetItem = sseClient.resolveTargetItem(command);

        assertThat(targetItem).isEqualTo("BlindsBedroom_Position");
    }

    // ---- 9. resolveTargetItem finds Control+SoundVolume for set_volume ----

    @Test
    void resolveTargetItemFindsControlSoundVolumeForSetVolume() {
        sseClient.populateCaches(List.of(
            equipment("TVLiving", "Living Room TV",
                List.of("Equipment", "Television"),
                member("Dimmer", "TVLiving_Volume", "65",
                    List.of("Point", "Control", "SoundVolume")))));

        var command = DeviceCommand.setVolume("TVLiving", 80, "test", "corr-6");
        String targetItem = sseClient.resolveTargetItem(command);

        assertThat(targetItem).isEqualTo("TVLiving_Volume");
    }

    // ---- 10. cachedDevices returns defensive copy ----

    @Test
    void cachedDevicesReturnsDefensiveCopy() {
        sseClient.populateCaches(twoEquipments());

        var devices1 = sseClient.cachedDevices();
        var devices2 = sseClient.cachedDevices();

        assertThat(devices1).isNotSameAs(devices2);
        assertThat(devices1).containsExactlyInAnyOrderElementsOf(devices2);
    }

    // ---- 11. handleSseEvent with thermostat updates temperature ----

    @Test
    void handleSseEventUpdatesThermostatTemperature() throws Exception {
        sseClient.populateCaches(List.of(
            equipment("ThermostatHall", "Hall Thermostat",
                List.of("Equipment", "HVAC"),
                member("Number", "ThermostatHall_CurrentTemp", "20.0",
                    List.of("Point", "Measurement", "Temperature")),
                member("Number", "ThermostatHall_TargetTemp", "21.0",
                    List.of("Point", "Setpoint", "Temperature")),
                member("String", "ThermostatHall_Mode", "heat",
                    List.of()))));

        // Simulate current temp change to 22.5
        var payload = new OpenHabStatePayloadDto("Decimal", "22.5", "Decimal", "20.0");
        var payloadJson = objectMapper.writeValueAsString(payload);
        var event = new OpenHabSseEventDto(
            "openhab/items/ThermostatHall_CurrentTemp/statechanged", payloadJson, "ItemStateChangedEvent");
        String eventData = objectMapper.writeValueAsString(event);

        sseClient.handleSseEvent(eventData);

        var device = sseClient.cachedDevices().stream()
            .filter(d -> d.deviceId().equals("ThermostatHall")).findFirst().orElseThrow();
        assertThat(device).isInstanceOf(ThermostatDevice.class);
        var therm = (ThermostatDevice) device;
        assertThat(therm.currentTemperature().value())
            .isEqualByComparingTo(new java.math.BigDecimal("22.5"));
        // Target temp unchanged
        assertThat(therm.targetTemperature().value())
            .isEqualByComparingTo(new java.math.BigDecimal("21.0"));
    }

    // ---- 12. multiple SSE events for same equipment update incrementally ----

    @Test
    void multipleEventsForSameEquipmentUpdateIncrementally() throws Exception {
        sseClient.populateCaches(List.of(
            equipment("ThermostatHall", "Hall Thermostat",
                List.of("Equipment", "HVAC"),
                member("Number", "ThermostatHall_CurrentTemp", "20.0",
                    List.of("Point", "Measurement", "Temperature")),
                member("Number", "ThermostatHall_TargetTemp", "21.0",
                    List.of("Point", "Setpoint", "Temperature")),
                member("String", "ThermostatHall_Mode", "heat",
                    List.of()))));

        // First event: current temp changes
        var payload1 = new OpenHabStatePayloadDto("Decimal", "22.0", "Decimal", "20.0");
        var event1 = new OpenHabSseEventDto(
            "openhab/items/ThermostatHall_CurrentTemp/statechanged",
            objectMapper.writeValueAsString(payload1), "ItemStateChangedEvent");
        sseClient.handleSseEvent(objectMapper.writeValueAsString(event1));

        // Second event: target temp changes
        var payload2 = new OpenHabStatePayloadDto("Decimal", "23.0", "Decimal", "21.0");
        var event2 = new OpenHabSseEventDto(
            "openhab/items/ThermostatHall_TargetTemp/statechanged",
            objectMapper.writeValueAsString(payload2), "ItemStateChangedEvent");
        sseClient.handleSseEvent(objectMapper.writeValueAsString(event2));

        var device = sseClient.cachedDevices().stream()
            .filter(d -> d.deviceId().equals("ThermostatHall")).findFirst().orElseThrow();
        var therm = (ThermostatDevice) device;
        // Both updates should be reflected
        assertThat(therm.currentTemperature().value())
            .isEqualByComparingTo(new java.math.BigDecimal("22.0"));
        assertThat(therm.targetTemperature().value())
            .isEqualByComparingTo(new java.math.BigDecimal("23.0"));
    }

    // ---- 13. resolveTargetItem for turn_off finds same item as turn_on ----

    @Test
    void resolveTargetItemFindsSameItemForTurnOff() {
        sseClient.populateCaches(List.of(
            equipment("SwitchLiving", "Living Room Switch",
                List.of("Equipment", "PowerOutlet"),
                member("Switch", "SwitchLiving_Toggle", "ON",
                    List.of("Point", "Control", "Switch")))));

        var command = DeviceCommand.turnOff("SwitchLiving", "test", "corr-7");
        String targetItem = sseClient.resolveTargetItem(command);

        assertThat(targetItem).isEqualTo("SwitchLiving_Toggle");
    }
}

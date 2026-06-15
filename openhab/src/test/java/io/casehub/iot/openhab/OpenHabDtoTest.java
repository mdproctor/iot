package io.casehub.iot.openhab;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.iot.openhab.internal.OpenHabChannelDto;
import io.casehub.iot.openhab.internal.OpenHabItemDto;
import io.casehub.iot.openhab.internal.OpenHabSseEventDto;
import io.casehub.iot.openhab.internal.OpenHabStatePayloadDto;
import io.casehub.iot.openhab.internal.OpenHabStatusInfoDto;
import io.casehub.iot.openhab.internal.OpenHabThingDto;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OpenHabDtoTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void itemDtoDeserializesEquipmentGroupWithMembers() throws Exception {
        String json = """
            {
              "type": "Group",
              "name": "LivingRoom_Thermostat",
              "label": "Living Room Thermostat",
              "state": "NULL",
              "tags": ["Equipment", "HVAC"],
              "members": [
                {
                  "type": "Number:Temperature",
                  "name": "LivingRoom_Temperature",
                  "label": "Current Temperature",
                  "state": "21.5",
                  "tags": ["Measurement", "Temperature"],
                  "stateDescription": {"pattern": "%.1f °C"}
                },
                {
                  "type": "Number:Temperature",
                  "name": "LivingRoom_Setpoint",
                  "label": "Target Temperature",
                  "state": "22.0",
                  "tags": ["Setpoint", "Temperature"],
                  "stateDescription": {"pattern": "%.1f °C"}
                }
              ]
            }
            """;
        OpenHabItemDto item = mapper.readValue(json, OpenHabItemDto.class);
        assertThat(item.type()).isEqualTo("Group");
        assertThat(item.name()).isEqualTo("LivingRoom_Thermostat");
        assertThat(item.tags()).containsExactly("Equipment", "HVAC");
        assertThat(item.members()).hasSize(2);
        assertThat(item.members().get(0).name()).isEqualTo("LivingRoom_Temperature");
        assertThat(item.members().get(0).stateDescription()).isNotNull();
        assertThat(item.members().get(0).stateDescription().pattern()).isEqualTo("%.1f °C");
    }

    @Test
    void itemDtoIgnoresUnknownFields() throws Exception {
        String json = """
            {"type":"Switch","name":"Light_1","state":"ON","tags":[],"unknownField":"value"}
            """;
        OpenHabItemDto item = mapper.readValue(json, OpenHabItemDto.class);
        assertThat(item.name()).isEqualTo("Light_1");
    }

    @Test
    void sseEventDtoDeserializes() throws Exception {
        String json = """
            {
              "topic": "openhab/items/LivingRoom_Temperature/statechanged",
              "payload": "{\\"type\\":\\"Decimal\\",\\"value\\":\\"22.0\\",\\"oldType\\":\\"Decimal\\",\\"oldValue\\":\\"21.5\\"}",
              "type": "ItemStateChangedEvent"
            }
            """;
        OpenHabSseEventDto event = mapper.readValue(json, OpenHabSseEventDto.class);
        assertThat(event.topic()).isEqualTo("openhab/items/LivingRoom_Temperature/statechanged");
        assertThat(event.type()).isEqualTo("ItemStateChangedEvent");

        OpenHabStatePayloadDto payload = mapper.readValue(event.payload(), OpenHabStatePayloadDto.class);
        assertThat(payload.type()).isEqualTo("Decimal");
        assertThat(payload.value()).isEqualTo("22.0");
        assertThat(payload.oldValue()).isEqualTo("21.5");
    }

    @Test
    void thingDtoDeserializesFromJson() throws Exception {
        String json = """
            {
              "UID": "zwave:device:controller:node15",
              "label": "Living Room Thermostat",
              "thingTypeUID": "zwave:device",
              "statusInfo": {
                "status": "ONLINE",
                "statusDetail": "NONE"
              },
              "channels": [
                {
                  "uid": "zwave:device:controller:node15:switch_binary",
                  "id": "switch_binary",
                  "channelTypeUID": "zwave:switch_binary",
                  "itemType": "Switch",
                  "kind": "STATE",
                  "linkedItems": ["LivingRoom_HVAC_Power"],
                  "defaultTags": ["Switch"]
                },
                {
                  "uid": "zwave:device:controller:node15:alarm_motion",
                  "id": "alarm_motion",
                  "channelTypeUID": "zwave:alarm_motion",
                  "itemType": "Switch",
                  "kind": "TRIGGER",
                  "linkedItems": [],
                  "defaultTags": ["Alarm"]
                }
              ],
              "location": "Living Room"
            }
            """;
        OpenHabThingDto thing = mapper.readValue(json, OpenHabThingDto.class);
        assertThat(thing.uid()).isEqualTo("zwave:device:controller:node15");
        assertThat(thing.label()).isEqualTo("Living Room Thermostat");
        assertThat(thing.thingTypeUID()).isEqualTo("zwave:device");
        assertThat(thing.location()).isEqualTo("Living Room");

        assertThat(thing.statusInfo()).isNotNull();
        assertThat(thing.statusInfo().status()).isEqualTo("ONLINE");
        assertThat(thing.statusInfo().statusDetail()).isEqualTo("NONE");
        assertThat(thing.isOnline()).isTrue();

        assertThat(thing.channels()).hasSize(2);
        OpenHabChannelDto stateChannel = thing.channels().get(0);
        assertThat(stateChannel.uid()).isEqualTo("zwave:device:controller:node15:switch_binary");
        assertThat(stateChannel.id()).isEqualTo("switch_binary");
        assertThat(stateChannel.channelTypeUID()).isEqualTo("zwave:switch_binary");
        assertThat(stateChannel.itemType()).isEqualTo("Switch");
        assertThat(stateChannel.kind()).isEqualTo("STATE");
        assertThat(stateChannel.linkedItems()).containsExactly("LivingRoom_HVAC_Power");
        assertThat(stateChannel.defaultTags()).containsExactly("Switch");
        assertThat(stateChannel.isStateChannel()).isTrue();

        OpenHabChannelDto triggerChannel = thing.channels().get(1);
        assertThat(triggerChannel.kind()).isEqualTo("TRIGGER");
        assertThat(triggerChannel.isStateChannel()).isFalse();

        assertThat(thing.stateChannels()).hasSize(1);
        assertThat(thing.stateChannels().get(0).id()).isEqualTo("switch_binary");
    }

    @Test
    void thingStatusPayloadDeserializesAsArray() throws Exception {
        String json = """
            [
              {
                "status": "ONLINE",
                "statusDetail": "NONE"
              },
              {
                "status": "OFFLINE",
                "statusDetail": "COMMUNICATION_ERROR"
              }
            ]
            """;
        List<OpenHabStatusInfoDto> statusList = mapper.readValue(json, new TypeReference<List<OpenHabStatusInfoDto>>() {});
        assertThat(statusList).hasSize(2);
        assertThat(statusList.get(0).status()).isEqualTo("ONLINE");
        assertThat(statusList.get(0).statusDetail()).isEqualTo("NONE");
        assertThat(statusList.get(1).status()).isEqualTo("OFFLINE");
        assertThat(statusList.get(1).statusDetail()).isEqualTo("COMMUNICATION_ERROR");
    }
}

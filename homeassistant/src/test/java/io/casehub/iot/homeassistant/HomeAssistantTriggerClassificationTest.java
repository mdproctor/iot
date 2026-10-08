package io.casehub.iot.homeassistant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.iot.api.TriggerSource;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class HomeAssistantTriggerClassificationTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void userIdPresentWithoutParentId_manual() throws Exception {
        var json = mapper.readTree("""
            {"event": {"context": {"user_id": "abc123", "parent_id": null}}}
            """);
        assertThat(HomeAssistantWebSocketClient.classifyTrigger(json)).isEqualTo(TriggerSource.MANUAL);
    }

    @Test
    void parentIdReferencingAutomation_automation() throws Exception {
        var json = mapper.readTree("""
            {"event": {"context": {"user_id": null, "parent_id": "auto_abc"}}}
            """);
        assertThat(HomeAssistantWebSocketClient.classifyTrigger(json)).isEqualTo(TriggerSource.AUTOMATION);
    }

    @Test
    void motionDeviceClass_motion() throws Exception {
        var json = mapper.readTree("""
            {
              "event": {
                "context": {"user_id": null, "parent_id": null},
                "data": {"new_state": {"attributes": {"device_class": "motion"}}}
              }
            }
            """);
        assertThat(HomeAssistantWebSocketClient.classifyTrigger(json)).isEqualTo(TriggerSource.MOTION);
    }

    @Test
    void occupancyDeviceClass_motion() throws Exception {
        var json = mapper.readTree("""
            {
              "event": {
                "context": {"user_id": null, "parent_id": null},
                "data": {"new_state": {"attributes": {"device_class": "occupancy"}}}
              }
            }
            """);
        assertThat(HomeAssistantWebSocketClient.classifyTrigger(json)).isEqualTo(TriggerSource.MOTION);
    }

    @Test
    void noContextClues_unknown() throws Exception {
        var json = mapper.readTree("""
            {"event": {"context": {"user_id": null, "parent_id": null}}}
            """);
        assertThat(HomeAssistantWebSocketClient.classifyTrigger(json)).isEqualTo(TriggerSource.UNKNOWN);
    }

    @Test
    void missingContext_unknown() throws Exception {
        var json = mapper.readTree("""
            {"event": {}}
            """);
        assertThat(HomeAssistantWebSocketClient.classifyTrigger(json)).isEqualTo(TriggerSource.UNKNOWN);
    }

    @Test
    void missingEvent_unknown() throws Exception {
        var json = mapper.readTree("{}");
        assertThat(HomeAssistantWebSocketClient.classifyTrigger(json)).isEqualTo(TriggerSource.UNKNOWN);
    }
}

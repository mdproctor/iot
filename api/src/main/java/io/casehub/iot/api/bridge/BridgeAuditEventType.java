package io.casehub.iot.api.bridge;

public enum BridgeAuditEventType {
    STATE_CHANGE,
    REPLAYED_STATE_CHANGE,
    STATE_SNAPSHOT,
    PROVIDER_STATUS_CHANGE,
    COMMAND_SENT,
    COMMAND_RESPONSE,
    AGENT_CONNECTED,
    AGENT_DISCONNECTED
}

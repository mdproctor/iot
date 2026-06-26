package io.casehub.iot.api.bridge;

import jakarta.annotation.Nullable;
import java.time.Instant;

public record BridgeAuditEvent(
    String tenancyId,
    Instant receivedAt,
    BridgeAuditEventType eventType,
    @Nullable String correlationId,
    @Nullable String deviceId,
    @Nullable BridgeMessage message
) {}

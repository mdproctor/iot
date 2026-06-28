package io.casehub.iot.api.bridge;

import jakarta.annotation.Nullable;

import java.time.Instant;

public record BridgeAuditQuery(
    @Nullable String tenancyId,
    @Nullable Instant from,
    @Nullable Instant to,
    @Nullable BridgeAuditEventType eventType,
    @Nullable String deviceId,
    @Nullable String correlationId,
    int limit
) {
    public static final int DEFAULT_LIMIT = 100;

    public BridgeAuditQuery {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private String tenancyId;
        private Instant from;
        private Instant to;
        private BridgeAuditEventType eventType;
        private String deviceId;
        private String correlationId;
        private int limit = DEFAULT_LIMIT;

        public Builder tenancyId(final String tenancyId) { this.tenancyId = tenancyId; return this; }
        public Builder from(final Instant from) { this.from = from; return this; }
        public Builder to(final Instant to) { this.to = to; return this; }
        public Builder eventType(final BridgeAuditEventType eventType) { this.eventType = eventType; return this; }
        public Builder deviceId(final String deviceId) { this.deviceId = deviceId; return this; }
        public Builder correlationId(final String correlationId) { this.correlationId = correlationId; return this; }
        public Builder limit(final int limit) { this.limit = limit; return this; }

        public BridgeAuditQuery build() {
            return new BridgeAuditQuery(tenancyId, from, to, eventType, deviceId, correlationId, limit);
        }
    }
}

package io.casehub.iot.bridge.persistence.jpa;

import io.casehub.iot.api.bridge.BridgeAuditEvent;

final class BridgeAuditEventMapper {

    private BridgeAuditEventMapper() {}

    static BridgeAuditJpaEntity toEntity(final BridgeAuditEvent event) {
        return new BridgeAuditJpaEntity(
            event.tenancyId(),
            event.receivedAt(),
            event.eventType(),
            event.correlationId(),
            event.deviceId(),
            event.message()
        );
    }

    static BridgeAuditEvent toDomain(final BridgeAuditJpaEntity entity) {
        return new BridgeAuditEvent(
            entity.getTenancyId(),
            entity.getReceivedAt(),
            entity.getEventType(),
            entity.getCorrelationId(),
            entity.getDeviceId(),
            entity.getMessage()
        );
    }
}

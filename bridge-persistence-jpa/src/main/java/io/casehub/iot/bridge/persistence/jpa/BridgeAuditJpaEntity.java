package io.casehub.iot.bridge.persistence.jpa;

import io.casehub.iot.api.bridge.BridgeAuditEventType;
import io.casehub.iot.api.bridge.BridgeMessage;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "bridge_audit_event")
public class BridgeAuditJpaEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenancy_id", nullable = false)
    private String tenancyId;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 50)
    private BridgeAuditEventType eventType;

    @Column(name = "correlation_id")
    private String correlationId;

    @Column(name = "device_id")
    private String deviceId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "message", columnDefinition = "jsonb")
    private BridgeMessage message;

    protected BridgeAuditJpaEntity() {}

    public BridgeAuditJpaEntity(final String tenancyId, final Instant receivedAt,
                                 final BridgeAuditEventType eventType,
                                 final String correlationId, final String deviceId,
                                 final BridgeMessage message) {
        this.tenancyId = tenancyId;
        this.receivedAt = receivedAt;
        this.eventType = eventType;
        this.correlationId = correlationId;
        this.deviceId = deviceId;
        this.message = message;
    }

    public UUID getId() { return id; }
    public String getTenancyId() { return tenancyId; }
    public Instant getReceivedAt() { return receivedAt; }
    public BridgeAuditEventType getEventType() { return eventType; }
    public String getCorrelationId() { return correlationId; }
    public String getDeviceId() { return deviceId; }
    public BridgeMessage getMessage() { return message; }
}

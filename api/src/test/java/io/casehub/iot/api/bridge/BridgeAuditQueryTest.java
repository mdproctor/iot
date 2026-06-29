package io.casehub.iot.api.bridge;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BridgeAuditQueryTest {

    @Test
    void builderProducesQueryWithAllFields() {
        final Instant from = Instant.now().minus(1, ChronoUnit.HOURS);
        final Instant to = Instant.now();
        final var query = BridgeAuditQuery.builder()
            .tenancyId("tenant-1")
            .from(from)
            .to(to)
            .eventType(BridgeAuditEventType.COMMAND_SENT)
            .deviceId("light.kitchen")
            .correlationId("corr-123")
            .limit(50)
            .build();

        assertThat(query.tenancyId()).isEqualTo("tenant-1");
        assertThat(query.from()).isEqualTo(from);
        assertThat(query.to()).isEqualTo(to);
        assertThat(query.eventType()).isEqualTo(BridgeAuditEventType.COMMAND_SENT);
        assertThat(query.deviceId()).isEqualTo("light.kitchen");
        assertThat(query.correlationId()).isEqualTo("corr-123");
        assertThat(query.limit()).isEqualTo(50);
    }

    @Test
    void builderDefaultsLimitTo100WithAllCriteriaNull() {
        final var query = BridgeAuditQuery.builder().build();

        assertThat(query.tenancyId()).isNull();
        assertThat(query.from()).isNull();
        assertThat(query.to()).isNull();
        assertThat(query.eventType()).isNull();
        assertThat(query.deviceId()).isNull();
        assertThat(query.correlationId()).isNull();
        assertThat(query.limit()).isEqualTo(100);
    }

    @Test
    void compactConstructorRejectsZeroLimit() {
        assertThatThrownBy(() -> BridgeAuditQuery.builder().limit(0).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("limit must be positive");
    }

    @Test
    void compactConstructorRejectsNegativeLimit() {
        assertThatThrownBy(() -> BridgeAuditQuery.builder().limit(-1).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("limit must be positive");
    }

    @Test
    void defaultLimitConstantIs100() {
        assertThat(BridgeAuditQuery.DEFAULT_LIMIT).isEqualTo(100);
    }

    @Test
    void offsetMustBeNonNegative() {
        assertThatThrownBy(() -> BridgeAuditQuery.builder().offset(-1).build())
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("offset");
    }

    @Test
    void offsetDefaultsToZero() {
        final var query = BridgeAuditQuery.builder().build();
        assertThat(query.offset()).isZero();
    }
}

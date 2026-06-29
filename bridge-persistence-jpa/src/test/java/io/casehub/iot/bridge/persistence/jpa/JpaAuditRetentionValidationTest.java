package io.casehub.iot.bridge.persistence.jpa;

import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JpaAuditRetentionValidationTest {

    @Test
    void rejectsZeroRetentionDays() {
        assertThatThrownBy(() -> new JpaAuditRetentionJob(null, configWith(0)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("retention-days must be >= 1");
    }

    @Test
    void rejectsNegativeRetentionDays() {
        assertThatThrownBy(() -> new JpaAuditRetentionJob(null, configWith(-1)))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("retention-days must be >= 1");
    }

    private static JpaAuditRetentionConfig configWith(final int days) {
        return new JpaAuditRetentionConfig() {
            @Override
            public Optional<Integer> retentionDays() {
                return Optional.of(days);
            }

            @Override
            public Duration purgeInterval() {
                return Duration.ofHours(24);
            }
        };
    }
}

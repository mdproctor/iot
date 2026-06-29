package io.casehub.iot.bridge.persistence.jpa;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.time.Duration;
import java.util.Optional;

@ConfigMapping(prefix = "casehub.iot.bridge.audit-store.jpa")
public interface JpaAuditRetentionConfig {

    Optional<Integer> retentionDays();

    @WithDefault("24h")
    Duration purgeInterval();
}

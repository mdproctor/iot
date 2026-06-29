package io.casehub.iot.bridge.persistence.memory;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

@ConfigMapping(prefix = "casehub.iot.bridge.audit-store.memory")
public interface InMemoryAuditStoreConfig {

    @WithDefault("10000")
    int maxSize();
}

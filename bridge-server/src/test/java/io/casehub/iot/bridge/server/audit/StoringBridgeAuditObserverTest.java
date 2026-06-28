package io.casehub.iot.bridge.server.audit;

import io.casehub.iot.api.bridge.BridgeAuditEvent;
import io.casehub.iot.api.bridge.BridgeAuditEventType;
import io.casehub.iot.api.bridge.BridgeAuditQuery;
import io.casehub.iot.api.bridge.BridgeAuditStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class StoringBridgeAuditObserverTest {

    @Test
    void onAuditDelegatesToStoreSave() {
        final var store = new InMemoryBridgeAuditStore(100);
        final var observer = new StoringBridgeAuditObserver(store);
        final var event = new BridgeAuditEvent(
            "tenant-1", Instant.now(), BridgeAuditEventType.STATE_CHANGE,
            null, "light.kitchen", null);

        observer.onAudit(event);

        final var results = store.query(BridgeAuditQuery.builder().build());
        assertThat(results).containsExactly(event);
    }
}

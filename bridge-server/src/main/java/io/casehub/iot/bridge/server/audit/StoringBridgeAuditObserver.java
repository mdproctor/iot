package io.casehub.iot.bridge.server.audit;

import io.casehub.iot.api.bridge.BridgeAuditEvent;
import io.casehub.iot.api.bridge.BridgeAuditStore;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;

@ApplicationScoped
public class StoringBridgeAuditObserver {

    private final BridgeAuditStore store;

    @Inject
    StoringBridgeAuditObserver(final BridgeAuditStore store) {
        this.store = store;
    }

    void onAudit(@ObservesAsync final BridgeAuditEvent event) {
        store.save(event);
    }
}

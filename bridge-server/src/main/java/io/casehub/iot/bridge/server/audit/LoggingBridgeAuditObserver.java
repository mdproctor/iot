package io.casehub.iot.bridge.server.audit;

import io.casehub.iot.api.bridge.BridgeAuditEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LoggingBridgeAuditObserver {

    private static final Logger LOG = Logger.getLogger("io.casehub.iot.bridge.audit");

    void onAudit(@ObservesAsync BridgeAuditEvent event) {
        LOG.infof("[AUDIT] type=%s tenancy=%s device=%s correlation=%s",
            event.eventType(), event.tenancyId(),
            event.deviceId(), event.correlationId());
    }
}

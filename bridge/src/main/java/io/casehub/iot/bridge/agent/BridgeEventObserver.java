package io.casehub.iot.bridge.agent;

import io.casehub.iot.api.ProviderStatusEvent;
import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.api.bridge.BridgeMessage;
import io.casehub.iot.api.bridge.ConnectionState;
import io.casehub.iot.api.bridge.FilterAction;
import io.casehub.iot.api.bridge.FilterContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.ObservesAsync;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.time.Instant;

/**
 * Observes CDI events from local device providers and forwards them through
 * the bridge filter chain to the cloud endpoint. Events are only forwarded
 * when the WebSocket connection is active.
 */
@ApplicationScoped
public class BridgeEventObserver {

    private static final Logger LOG = Logger.getLogger(BridgeEventObserver.class);

    @Inject
    BridgeFilterChain filterChain;

    @Inject
    BridgeConnectionManager connectionManager;

    @Inject
    BridgeAgentConfig config;

    void onStateChange(@ObservesAsync StateChangeEvent event) {
        if (!connectionManager.isConnected()) {
            return;
        }

        FilterContext ctx = new FilterContext(
                config.tenancyId(),
                ConnectionState.CONNECTED,
                event.providerId());
        FilterAction action = filterChain.execute(event, ctx).await().indefinitely();

        switch (action) {
            case FilterAction.Forward f -> {
                var msg = new BridgeMessage.StateChange(
                        config.tenancyId(), Instant.now(), event);
                connectionManager.send(msg);
            }
            case FilterAction.Suppress s ->
                    LOG.debugf("Event suppressed: %s", s.reason());
        }
    }

    void onProviderStatus(@ObservesAsync ProviderStatusEvent event) {
        if (!connectionManager.isConnected()) {
            return;
        }
        var msg = new BridgeMessage.ProviderStatusChange(
                config.tenancyId(), Instant.now(), event);
        connectionManager.send(msg);
    }
}

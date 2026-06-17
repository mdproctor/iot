package io.casehub.iot.bridge.server;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.casehub.iot.api.ProviderStatusEvent;
import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.api.bridge.BridgeMessage;
import io.quarkus.websockets.next.CloseReason;
import io.quarkus.websockets.next.OnClose;
import io.quarkus.websockets.next.OnOpen;
import io.quarkus.websockets.next.OnTextMessage;
import io.quarkus.websockets.next.WebSocket;
import io.quarkus.websockets.next.WebSocketConnection;
import jakarta.enterprise.event.Event;
import jakarta.inject.Inject;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * WebSocket server endpoint that accepts connections from bridge agents and routes
 * incoming messages to {@link BridgeDeviceProvider}.
 *
 * <p>Each connecting agent must supply an {@code X-Tenancy-ID} header during the
 * WebSocket handshake. Connections without a valid tenancy ID are closed immediately.
 *
 * <p>Message flow is agent-to-server: state changes, snapshots, provider status, command
 * results, and heartbeats. Commands flow server-to-agent and are handled separately.
 */
@WebSocket(path = "/iot/bridge")
public class BridgeWebSocketEndpoint {

    private static final Logger LOG = Logger.getLogger(BridgeWebSocketEndpoint.class);
    private static final String TENANCY_HEADER = "X-Tenancy-ID";

    @Inject ObjectMapper mapper;
    @Inject BridgeDeviceProvider provider;
    @Inject BridgeConnectionRegistry connectionRegistry;
    @Inject Event<StateChangeEvent> stateEvents;
    @Inject Event<ProviderStatusEvent> statusEvents;

    @OnOpen
    void onOpen(WebSocketConnection connection) {
        String tenancyId = connection.handshakeRequest().header(TENANCY_HEADER);
        if (tenancyId == null || tenancyId.isBlank()) {
            LOG.warnf("Bridge connection rejected: missing or blank %s header [remote=%s]",
                    TENANCY_HEADER, connection.handshakeRequest().remoteAddress());
            connection.closeAndAwait(new CloseReason(4001, "Missing " + TENANCY_HEADER + " header"));
            return;
        }
        connectionRegistry.register(tenancyId, connection);
        LOG.infof("Bridge agent connected [tenancyId=%s, connectionId=%s]", tenancyId, connection.id());
    }

    @OnClose
    void onClose(WebSocketConnection connection) {
        String tenancyId = connection.handshakeRequest().header(TENANCY_HEADER);
        if (tenancyId != null && !tenancyId.isBlank()) {
            connectionRegistry.unregister(tenancyId);
            LOG.infof("Bridge agent disconnected [tenancyId=%s, connectionId=%s]", tenancyId, connection.id());
        }
    }

    @OnTextMessage
    void onMessage(String text, WebSocketConnection connection) {
        String tenancyId = connection.handshakeRequest().header(TENANCY_HEADER);

        BridgeMessage message;
        try {
            message = mapper.readValue(text, BridgeMessage.class);
        } catch (JsonProcessingException e) {
            LOG.warnf("Failed to deserialize bridge message [tenancyId=%s]: %s", tenancyId, e.getMessage());
            return;
        }

        if (!tenancyId.equals(message.tenancyId())) {
            LOG.warnf("Tenancy mismatch: header=%s, message=%s — using header value",
                    tenancyId, message.tenancyId());
        }

        switch (message) {
            case BridgeMessage.StateChange sc -> {
                StateChangeEvent namespacedEvent = provider.onStateChange(sc.event(), tenancyId);
                stateEvents.fireAsync(namespacedEvent);
            }
            case BridgeMessage.StateSnapshot ss -> {
                List<StateChangeEvent> events = provider.onSnapshot(tenancyId, ss.devices());
                for (StateChangeEvent event : events) {
                    stateEvents.fireAsync(event);
                }
            }
            case BridgeMessage.ProviderStatusChange ps -> {
                statusEvents.fireAsync(ps.status());
            }
            case BridgeMessage.CommandResponse cr -> {
                LOG.debugf("Command result received [tenancyId=%s, correlationId=%s, result=%s]",
                        tenancyId, cr.correlationId(), cr.result());
                provider.completeCommand(tenancyId, cr.correlationId(), cr.result());
            }
            case BridgeMessage.Heartbeat hb -> {
                // No action required — heartbeats keep the connection alive
            }
            case BridgeMessage.Command cmd -> {
                LOG.warnf("Received Command message from agent — commands flow server-to-agent, "
                        + "not agent-to-server [tenancyId=%s]", tenancyId);
            }
        }
    }
}

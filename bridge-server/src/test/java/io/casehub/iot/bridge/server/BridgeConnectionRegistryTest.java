package io.casehub.iot.bridge.server;

import io.quarkus.websockets.next.WebSocketConnection;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeConnectionRegistryTest {

    private BridgeConnectionRegistry registry;

    @BeforeEach
    void setUp() {
        registry = new BridgeConnectionRegistry();
    }

    @Test
    void registerAndLookup() {
        var session = mockConnection();
        registry.register("site-a", session);

        assertThat(registry.getSession("site-a")).isPresent();
    }

    @Test
    void unregisterRemovesSession() {
        var session = mockConnection();
        registry.register("site-a", session);

        registry.unregister("site-a");

        assertThat(registry.getSession("site-a")).isEmpty();
    }

    @Test
    void allConnectedTenancies() {
        registry.register("site-a", mockConnection());
        registry.register("site-b", mockConnection());

        assertThat(registry.connectedTenancies()).containsExactlyInAnyOrder("site-a", "site-b");
    }

    @Test
    void isFullyConnectedWhenAllKnownTenanciesHaveSessions() {
        registry.register("site-a", mockConnection());
        registry.register("site-b", mockConnection());

        assertThat(registry.isFullyConnected()).isTrue();
    }

    @Test
    void notFullyConnectedAfterDisconnect() {
        registry.register("site-a", mockConnection());
        registry.register("site-b", mockConnection());

        registry.unregister("site-b");

        // site-b is still a known tenancy, but no longer connected
        assertThat(registry.isFullyConnected()).isFalse();
        assertThat(registry.hasAnyConnection()).isTrue();
    }

    @Test
    void noConnectionsWhenEmpty() {
        assertThat(registry.isFullyConnected()).isTrue();
        assertThat(registry.hasAnyConnection()).isFalse();
    }

    private static WebSocketConnection mockConnection() {
        return (WebSocketConnection) Proxy.newProxyInstance(
                WebSocketConnection.class.getClassLoader(),
                new Class[]{WebSocketConnection.class},
                (proxy, method, args) -> null);
    }
}

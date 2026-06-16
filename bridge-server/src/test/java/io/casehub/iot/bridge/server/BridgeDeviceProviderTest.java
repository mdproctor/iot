package io.casehub.iot.bridge.server;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import io.casehub.iot.api.CommandResult;
import io.casehub.iot.api.DeviceClass;
import io.casehub.iot.api.DeviceCommand;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.LightDevice;
import io.casehub.iot.api.ProviderStatus;
import io.casehub.iot.api.StateChangeEvent;
import io.casehub.iot.api.SwitchDevice;
import io.casehub.iot.api.ThermostatDevice;
import io.casehub.iot.testing.Fixtures;
import io.quarkus.websockets.next.WebSocketConnection;
import org.junit.jupiter.api.BeforeAll;

import java.lang.reflect.Proxy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeDeviceProviderTest {

    private static DeviceIdNamespacer namespacer;

    private BridgeConnectionRegistry registry;
    private BridgeDeviceProvider provider;

    @BeforeAll
    static void initNamespacer() {
        JsonMapper mapper = JsonMapper.builder()
                .addModule(new JavaTimeModule())
                .build();
        namespacer = new DeviceIdNamespacer(mapper);
    }

    @BeforeEach
    void setUp() {
        registry = new BridgeConnectionRegistry();
        provider = new BridgeDeviceProvider(namespacer, registry);
    }

    @Test
    void providerIdIsBridge() {
        assertThat(provider.providerId()).isEqualTo("bridge");
    }

    @Test
    void snapshotPopulatesDeviceMap() {
        SwitchDevice sw = Fixtures.hallwaySwitch();
        LightDevice light = Fixtures.livingRoomLight();

        provider.onSnapshot("site-a", List.of(sw, light));

        List<DeviceEntity> discovered = provider.discover().await().indefinitely();
        assertThat(discovered).hasSize(2);
        assertThat(discovered).extracting(DeviceEntity::deviceId)
                .containsExactlyInAnyOrder("site-a/switch-hallway-1", "site-a/light-living-1");
    }

    @Test
    void snapshotDiffDetectsStateChange() {
        SwitchDevice switchOff = Fixtures.hallwaySwitch();
        provider.onSnapshot("site-a", List.of(switchOff));

        // Second snapshot: switch is now on
        SwitchDevice switchOn = SwitchDevice.builder()
                .deviceId("switch-hallway-1").deviceClass(DeviceClass.SWITCH)
                .label("Hallway Switch").available(true).lastUpdated(Fixtures.EPOCH)
                .tenancyId(Fixtures.DEFAULT_TENANT).on(true).build();

        List<StateChangeEvent> events = provider.onSnapshot("site-a", List.of(switchOn));

        assertThat(events).hasSize(1);
        StateChangeEvent event = events.get(0);
        assertThat(event.before()).isNotNull();
        assertThat(event.before().deviceId()).isEqualTo("site-a/switch-hallway-1");
        assertThat(event.after().deviceId()).isEqualTo("site-a/switch-hallway-1");
        assertThat(event.changedCapabilities()).contains(SwitchDevice.CAP_ON);
        assertThat(event.providerId()).isEqualTo("bridge");
    }

    @Test
    void snapshotDiffDetectsNewDevice() {
        SwitchDevice sw = Fixtures.hallwaySwitch();
        provider.onSnapshot("site-a", List.of(sw));

        // Second snapshot adds a new light
        LightDevice light = Fixtures.livingRoomLight();
        List<StateChangeEvent> events = provider.onSnapshot("site-a", List.of(sw, light));

        // Switch unchanged, light is new
        List<StateChangeEvent> newDeviceEvents = events.stream()
                .filter(e -> e.after().deviceId().equals("site-a/light-living-1"))
                .toList();
        assertThat(newDeviceEvents).hasSize(1);
        StateChangeEvent newEvent = newDeviceEvents.get(0);
        assertThat(newEvent.before()).isNull();
        assertThat(newEvent.changedCapabilities()).containsAll(newEvent.after().capabilities().keySet());
        assertThat(newEvent.providerId()).isEqualTo("bridge");
    }

    @Test
    void snapshotDiffDetectsRemovedDevice() {
        SwitchDevice sw = Fixtures.hallwaySwitch();
        LightDevice light = Fixtures.livingRoomLight();
        provider.onSnapshot("site-a", List.of(sw, light));

        // Second snapshot: light removed
        List<StateChangeEvent> events = provider.onSnapshot("site-a", List.of(sw));

        List<StateChangeEvent> removedEvents = events.stream()
                .filter(e -> e.after().deviceId().equals("site-a/light-living-1"))
                .toList();
        assertThat(removedEvents).hasSize(1);
        StateChangeEvent removed = removedEvents.get(0);
        assertThat(removed.after().available()).isFalse();
        assertThat(removed.changedCapabilities()).contains(DeviceEntity.CAP_AVAILABLE);
        assertThat(removed.providerId()).isEqualTo("bridge");
    }

    @Test
    void statusDisconnectedWhenNoAgents() {
        assertThat(provider.status()).isEqualTo(ProviderStatus.DISCONNECTED);
    }

    @Test
    void statusConnectedWhenAllAgentsConnected() {
        registry.register("site-a", mockConnection());
        registry.register("site-b", mockConnection());

        assertThat(provider.status()).isEqualTo(ProviderStatus.CONNECTED);
    }

    @Test
    void statusConnectingWhenPartiallyConnected() {
        registry.register("site-a", mockConnection());
        registry.register("site-b", mockConnection());

        registry.unregister("site-b");

        assertThat(provider.status()).isEqualTo(ProviderStatus.CONNECTING);
    }

    @Test
    void dispatchFailsWhenAgentNotConnected() {
        DeviceCommand cmd = DeviceCommand.turnOn("site-a/switch-hallway-1", Map.of(), "test", "corr-1");

        CommandResult result = provider.dispatch(cmd).await().indefinitely();

        assertThat(result).isEqualTo(CommandResult.FAILED);
    }

    @Test
    void snapshotNoEventsWhenUnchanged() {
        SwitchDevice sw = Fixtures.hallwaySwitch();
        provider.onSnapshot("site-a", List.of(sw));

        // Same snapshot again — no changes
        List<StateChangeEvent> events = provider.onSnapshot("site-a", List.of(sw));

        assertThat(events).isEmpty();
    }

    @Test
    void multiTenancyIsolation() {
        SwitchDevice sw = Fixtures.hallwaySwitch();
        LightDevice light = Fixtures.livingRoomLight();

        provider.onSnapshot("site-a", List.of(sw));
        provider.onSnapshot("site-b", List.of(light));

        List<DeviceEntity> discovered = provider.discover().await().indefinitely();
        assertThat(discovered).hasSize(2);
        assertThat(discovered).extracting(DeviceEntity::deviceId)
                .containsExactlyInAnyOrder("site-a/switch-hallway-1", "site-b/light-living-1");
    }

    private static WebSocketConnection mockConnection() {
        return (WebSocketConnection) Proxy.newProxyInstance(
                WebSocketConnection.class.getClassLoader(),
                new Class[]{WebSocketConnection.class},
                (proxy, method, args) -> null);
    }
}

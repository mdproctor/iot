package io.casehub.iot.scenario;

import io.casehub.iot.api.CommandResult;
import io.casehub.iot.api.PlaybookBindingEvent;
import io.casehub.iot.testing.Fixtures;
import io.casehub.iot.testing.MockDeviceProvider;
import io.casehub.iot.testing.MockDeviceRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeviceCommandDispatcherTest {

    private MockDeviceProvider provider;
    private DeviceCommandDispatcher dispatcher;
    private List<PlaybookBindingEvent> capturedBindingEvents;

    @BeforeEach
    void setUp() {
        provider = new MockDeviceProvider("test");
        Fixtures.standardHome().forEach(provider::addDevice);
        var registry = new MockDeviceRegistry();
        registry.addDevices(provider.discover());
        capturedBindingEvents = new ArrayList<>();
        dispatcher = new DeviceCommandDispatcher(
                registry, List.of(provider), "test-tenant", capturedBindingEvents::add);
    }

    @Test
    void dispatch_sent_returns_sent() {
        provider.setDispatchResult(CommandResult.SENT);
        var result = dispatcher.dispatch("light-living-1", "turn_on", Map.of(), "corr-1");
        assertThat(result).isEqualTo(CommandResult.SENT);
        assertThat(provider.dispatchedCommands()).hasSize(1);
        assertThat(provider.dispatchedCommands().get(0).action()).isEqualTo("turn_on");
    }

    @Test
    void dispatch_failed_returns_failed() {
        provider.setDispatchResult(CommandResult.FAILED);
        var result = dispatcher.dispatch("light-living-1", "turn_on", Map.of(), "corr-1");
        assertThat(result).isEqualTo(CommandResult.FAILED);
    }

    @Test
    void dispatch_timeout_returns_timeout() {
        provider.setDispatchResult(CommandResult.TIMEOUT);
        var result = dispatcher.dispatch("light-living-1", "turn_on", Map.of(), "corr-1");
        assertThat(result).isEqualTo(CommandResult.TIMEOUT);
    }

    @Test
    void dispatch_unknown_device_throws() {
        assertThatThrownBy(() -> dispatcher.dispatch("nonexistent", "turn_on", Map.of(), "corr-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Device not found");
    }

    @Test
    void dispatch_unknown_provider_throws() {
        var registry = new MockDeviceRegistry();
        Fixtures.standardHome().forEach(registry::addDevice);
        var emptyDispatcher = new DeviceCommandDispatcher(
                registry, List.of(), "test-tenant", e -> {});

        assertThatThrownBy(() -> emptyDispatcher.dispatch("light-living-1", "turn_on", Map.of(), "corr-1"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("No provider for");
    }

    @Test
    void findDevice_returns_entity() {
        assertThat(dispatcher.findDevice("light-living-1")).isPresent();
        assertThat(dispatcher.findDevice("nonexistent")).isEmpty();
    }

    @Test
    void dispatch_with_executionId_fires_binding_events_on_sent() {
        provider.setDispatchResult(CommandResult.SENT);
        dispatcher.dispatch("light-living-1", "turn_on", Map.of(), "corr-1", "exec-1");

        assertThat(capturedBindingEvents).hasSize(3);
        assertThat(capturedBindingEvents.get(0))
                .isInstanceOf(PlaybookBindingEvent.StepStart.class);
        var start = (PlaybookBindingEvent.StepStart) capturedBindingEvents.get(0);
        assertThat(start.executionId()).isEqualTo("exec-1");
        assertThat(start.deviceIds()).containsExactly("light-living-1");

        assertThat(capturedBindingEvents.get(1))
                .isInstanceOf(PlaybookBindingEvent.DeviceProvisioned.class);

        assertThat(capturedBindingEvents.get(2))
                .isInstanceOf(PlaybookBindingEvent.StepComplete.class);
        var complete = (PlaybookBindingEvent.StepComplete) capturedBindingEvents.get(2);
        assertThat(complete.provisioned()).isEqualTo(1);
        assertThat(complete.failed()).isZero();
    }

    @Test
    void dispatch_with_executionId_fires_binding_events_on_failed() {
        provider.setDispatchResult(CommandResult.FAILED);
        dispatcher.dispatch("light-living-1", "turn_on", Map.of(), "corr-1", "exec-2");

        assertThat(capturedBindingEvents).hasSize(3);
        assertThat(capturedBindingEvents.get(0))
                .isInstanceOf(PlaybookBindingEvent.StepStart.class);
        assertThat(capturedBindingEvents.get(1))
                .isInstanceOf(PlaybookBindingEvent.DeviceFailed.class);
        assertThat(capturedBindingEvents.get(2))
                .isInstanceOf(PlaybookBindingEvent.StepFailed.class);
    }

    @Test
    void dispatch_with_executionId_fires_binding_events_on_timeout() {
        provider.setDispatchResult(CommandResult.TIMEOUT);
        dispatcher.dispatch("light-living-1", "turn_on", Map.of(), "corr-1", "exec-3");

        assertThat(capturedBindingEvents).hasSize(3);
        assertThat(capturedBindingEvents.get(1))
                .isInstanceOf(PlaybookBindingEvent.DeviceFailed.class);
        var failed = (PlaybookBindingEvent.DeviceFailed) capturedBindingEvents.get(1);
        assertThat(failed.reason()).contains("timed out");
    }

    @Test
    void dispatch_without_executionId_fires_no_binding_events() {
        provider.setDispatchResult(CommandResult.SENT);
        dispatcher.dispatch("light-living-1", "turn_on", Map.of(), "corr-1", null);

        assertThat(capturedBindingEvents).isEmpty();
    }

    @Test
    void dispatch_backward_compatible_overload_fires_no_binding_events() {
        provider.setDispatchResult(CommandResult.SENT);
        dispatcher.dispatch("light-living-1", "turn_on", Map.of(), "corr-1");

        assertThat(capturedBindingEvents).isEmpty();
    }

    @Test
    void dispatch_with_executionId_fires_clear_on_exception() {
        provider.setDispatchException(new RuntimeException("provider crash"));
        assertThatThrownBy(() ->
                dispatcher.dispatch("light-living-1", "turn_on", Map.of(), "corr-1", "exec-4"))
                .isInstanceOf(RuntimeException.class)
                .hasMessageContaining("provider crash");

        assertThat(capturedBindingEvents).hasSize(2);
        assertThat(capturedBindingEvents.get(0))
                .isInstanceOf(PlaybookBindingEvent.StepStart.class);
        assertThat(capturedBindingEvents.get(1))
                .isInstanceOf(PlaybookBindingEvent.Clear.class);
    }
}

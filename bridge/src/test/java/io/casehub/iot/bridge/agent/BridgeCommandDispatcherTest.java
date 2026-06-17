package io.casehub.iot.bridge.agent;

import io.casehub.iot.api.CommandResult;
import io.casehub.iot.api.DeviceCommand;
import io.casehub.iot.testing.MockDeviceProvider;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BridgeCommandDispatcherTest {

    @Test
    void dispatchPassesCommandAsReceived() {
        var provider = new MockDeviceProvider("local-ha");
        provider.setDispatchResult(CommandResult.SENT);

        var dispatcher = new BridgeCommandDispatcher(List.of(provider));

        var command = new DeviceCommand(
                "switch-1", "turn_on", Map.of(), "cloud", "corr-1");

        CommandResult result = dispatcher.dispatch(command).await().indefinitely();

        assertThat(result).isEqualTo(CommandResult.SENT);
        assertThat(provider.dispatchedCommands()).hasSize(1);

        DeviceCommand dispatched = provider.dispatchedCommands().get(0);
        assertThat(dispatched.targetDeviceId()).isEqualTo("switch-1");
        assertThat(dispatched.action()).isEqualTo("turn_on");
        assertThat(dispatched.dispatchedBy()).isEqualTo("cloud");
        assertThat(dispatched.correlationId()).isEqualTo("corr-1");
    }
}

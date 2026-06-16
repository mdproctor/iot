package io.casehub.iot.bridge.agent;

import io.casehub.iot.api.CommandResult;
import io.casehub.iot.api.DeviceCommand;
import io.casehub.iot.api.bridge.DeviceIdUtils;
import io.casehub.iot.api.spi.DeviceProvider;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Receives commands from the cloud side, strips the tenancy prefix from the
 * device ID, and dispatches to the first available local {@link DeviceProvider}.
 */
@ApplicationScoped
public class BridgeCommandDispatcher {

    private final List<DeviceProvider> providers;

    @Inject
    public BridgeCommandDispatcher(@Any Instance<DeviceProvider> discovered) {
        this.providers = discovered.stream().toList();
    }

    /** Test constructor — accepts a pre-built provider list. */
    BridgeCommandDispatcher(List<DeviceProvider> providers) {
        this.providers = List.copyOf(providers);
    }

    /**
     * Strip the tenancy prefix from the command's target device ID and dispatch
     * to the first provider. Returns {@link CommandResult#FAILED} if no providers
     * are available.
     */
    public Uni<CommandResult> dispatch(DeviceCommand command) {
        if (providers.isEmpty()) {
            return Uni.createFrom().item(CommandResult.FAILED);
        }

        String localId = DeviceIdUtils.stripPrefix(command.targetDeviceId());
        DeviceCommand localCommand = new DeviceCommand(
                localId,
                command.action(),
                command.parameters(),
                command.dispatchedBy(),
                command.correlationId());

        return providers.get(0).dispatch(localCommand);
    }
}

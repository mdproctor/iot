package io.casehub.iot.bridge.agent;

import io.casehub.iot.api.CommandResult;
import io.casehub.iot.api.DeviceCommand;
import io.casehub.iot.api.spi.DeviceProvider;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import java.util.List;

/**
 * Receives commands from the cloud side and dispatches to the first available
 * local {@link DeviceProvider}. Commands arrive with local device IDs — the
 * server strips the tenancy prefix before sending.
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

    public Uni<CommandResult> dispatch(DeviceCommand command) {
        if (providers.isEmpty()) {
            return Uni.createFrom().item(CommandResult.FAILED);
        }
        return providers.get(0).dispatch(command);
    }
}

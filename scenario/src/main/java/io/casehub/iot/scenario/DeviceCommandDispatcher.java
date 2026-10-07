package io.casehub.iot.scenario;

import io.casehub.iot.api.CommandResult;
import io.casehub.iot.api.DeviceCommand;
import io.casehub.iot.api.DeviceEntity;
import io.casehub.iot.api.PlaybookBindingEvent;
import io.casehub.iot.api.spi.DeviceProvider;
import io.casehub.iot.api.spi.DeviceRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Any;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;

@ApplicationScoped
public class DeviceCommandDispatcher {

    private final DeviceRegistry registry;
    private final Map<String, DeviceProvider> providers;
    private final String tenancyId;
    private final Consumer<PlaybookBindingEvent> bindingEvent;

    @Inject
    public DeviceCommandDispatcher(
            DeviceRegistry registry,
            @Any Instance<DeviceProvider> providerBeans,
            @ConfigProperty(name = "casehub.iot.tenancy-id") String tenancyId,
            jakarta.enterprise.event.Event<PlaybookBindingEvent> bindingEvent) {
        this(registry, providerBeans.stream().toList(), tenancyId, bindingEvent::fire);
    }

    DeviceCommandDispatcher(DeviceRegistry registry, List<DeviceProvider> providerList,
                            String tenancyId, Consumer<PlaybookBindingEvent> bindingEvent) {
        this.registry = registry;
        this.providers = new HashMap<>();
        providerList.forEach(p -> providers.put(p.providerId(), p));
        this.tenancyId = tenancyId;
        this.bindingEvent = bindingEvent;
    }

    public CommandResult dispatch(String deviceId, String action,
                                  Map<String, Object> params, String correlationId) {
        return dispatch(deviceId, action, params, correlationId, null);
    }

    public CommandResult dispatch(String deviceId, String action,
                                  Map<String, Object> params,
                                  String correlationId,
                                  String executionId) {
        var optDevice = registry.findById(deviceId);
        if (optDevice.isEmpty()) {
            throw new IllegalArgumentException("Device not found: " + deviceId);
        }
        var entity = optDevice.get();

        var provider = providers.get(entity.providerId());
        if (provider == null) {
            throw new IllegalArgumentException("No provider for: " + entity.providerId());
        }

        var command = new DeviceCommand(
                deviceId, action, params != null ? params : Map.of(),
                "iot-scenario", correlationId);

        String stepLabel = "iot.command:" + action;

        boolean stepStartFired = false;

        if (executionId != null) {
            bindingEvent.accept(new PlaybookBindingEvent.StepStart(
                    executionId, tenancyId, stepLabel, Set.of(deviceId)));
            stepStartFired = true;
        }

        CommandResult result;
        try {
            result = provider.dispatch(command);
        } catch (RuntimeException e) {
            if (stepStartFired) {
                bindingEvent.accept(new PlaybookBindingEvent.Clear(executionId, tenancyId));
            }
            throw e;
        }

        if (executionId != null) {
            switch (result) {
                case SENT -> {
                    bindingEvent.accept(new PlaybookBindingEvent.DeviceProvisioned(
                            executionId, tenancyId, stepLabel, deviceId));
                    bindingEvent.accept(new PlaybookBindingEvent.StepComplete(
                            executionId, tenancyId, stepLabel, 1, 0));
                }
                case FAILED, TIMEOUT -> {
                    String reason = result == CommandResult.TIMEOUT
                            ? "Command timed out" : "Command failed";
                    bindingEvent.accept(new PlaybookBindingEvent.DeviceFailed(
                            executionId, tenancyId, stepLabel, deviceId, reason));
                    bindingEvent.accept(new PlaybookBindingEvent.StepFailed(
                            executionId, tenancyId, stepLabel,
                            0, 1, List.of(deviceId + ": " + reason)));
                }
            }
        }

        return result;
    }

    public Optional<DeviceEntity> findDevice(String deviceId) {
        return registry.findById(deviceId);
    }
}

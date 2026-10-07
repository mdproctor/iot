# Command Binding with Execution Context Threading

**Issue:** casehubio/iot#131
**Branch:** issue-131-command-binding-exec-context
**Status:** Design

## Problem

The scenario-topology binding (#129) fires `PlaybookBindingEvent` during desired-state delivery, enabling topology view highlighting during step execution. The command path (`IoTCommandPlugin` → `DeviceCommandDispatcher`) has no equivalent — commands cannot be correlated with scenario step executions because:

1. `IoTCommandPlugin` is a `@Plugin` record without CDI access
2. The Plugin SPI (`Action.execute(params, ServiceRegistry)`) does not thread execution context
3. `DeviceCommandDispatcher` does not receive or propagate an executionId
4. No CDI events are fired during command execution

## Design

### Architecture Overview

```
┌─────────────────────┐
│  Playbook Runtime    │  generates executionId per step
│  (pages-playbook)    │
└────────┬────────────┘
         │ populates
    ┌────▼────────────┐        ┌──────────────────────┐
    │ ServiceRegistry │        │ DeliveryContext       │
    │ (yaml-plugin-   │        │ (pages-playbook)      │
    │  api)            │        │ + executionId()       │
    └────┬────────────┘        └─────────┬────────────┘
         │ lookup                        │
    ┌────▼────────────┐        ┌─────────▼────────────┐
    │ IoTCommandPlugin │        │ DesiredStateDelivery- │
    │ @Execute method  │        │ Handler               │
    │ receives         │        │ reads ctx.executionId()│
    │ PluginExecution- │        │ instead of UUID.rand() │
    │ Context          │        └─────────┬────────────┘
    └────┬────────────┘                   │
         │ passes executionId             │ fires
    ┌────▼────────────┐        ┌─────────▼────────────┐
    │ DeviceCommand-   │        │ PlaybookBindingEvent  │
    │ Dispatcher       │        │ (same as today)       │
    │ + executionId    │        └──────────────────────┘
    │ fires binding    │
    │ events           │
    └────┬────────────┘
         │
    ┌────▼────────────┐
    │ PlaybookBinding- │
    │ Event            │
    │ StepStart →      │
    │ StepComplete/    │
    │ StepFailed       │
    └────┬────────────┘
         │ CDI observe
    ┌────▼────────────┐
    │ PlaybookTopology-│
    │ Binder           │   (already handles these events)
    └─────────────────┘
```

### Layer 1: Upstream SPI Changes

#### PluginExecutionContext (casehub-platform-yaml-plugin-api)

A new interface in `io.casehub.yaml.plugin.api`:

```java
public interface PluginExecutionContext {
    String executionId();
}
```

Minimal surface — just `executionId()` for now. Extensible later (e.g., `stepName()`, `tenancyId()`). Lives alongside `ServiceRegistry` and `@Execute`.

The runtime populates the `ServiceRegistry` with a `PluginExecutionContext` instance before calling `Action.execute()`. Plugins that need it add it as an `@Execute` method parameter — the annotation processor generates `services.lookup(PluginExecutionContext.class)`. Plugins that don't need it are unaffected.

**Null safety:** If the runtime doesn't populate the context (e.g., plugin executed outside a playbook), `services.lookup(PluginExecutionContext.class)` returns null. Plugins must handle this — use the context when present, generate a fallback UUID when absent.

#### DeliveryContext.executionId() (casehub-pages-playbook)

Add a default method to `DeliveryContext`:

```java
public interface DeliveryContext {
    String config(String key);
    String resolve(String template);
    Map<String, Object> resolveMap(Map<String, Object> data);

    default String executionId() {
        return null;
    }
}
```

Default returns null for backward compatibility. `RuntimeDeliveryContext` overrides it with the runtime-generated executionId.

#### RuntimeDeliveryContext update (casehub-pages-playbook-runtime)

The runtime generates an executionId per step and makes it available through both mechanisms:

```java
class RuntimeDeliveryContext implements DeliveryContext {

    private final PlaybookConfig config;
    private final VariableContext variables;
    private final String executionId;

    RuntimeDeliveryContext(PlaybookConfig config, VariableContext variables,
                           String executionId) {
        this.config = config;
        this.variables = variables;
        this.executionId = executionId;
    }

    @Override
    public String executionId() {
        return executionId;
    }
    // ... existing methods unchanged
}
```

`PlaybookExecutor.executeStep()` generates the executionId and populates both:
1. `RuntimeDeliveryContext` with the executionId
2. The `ServiceRegistry` with a `PluginExecutionContext` wrapping the same executionId

### Layer 2: IoT Plugin Changes (casehub-iot-scenario)

#### IoTCommandPlugin — accept PluginExecutionContext

```java
@Plugin(value = "iot.command",
        description = "Dispatches a command to an IoT device via DeviceProvider")
public record IoTCommandPlugin(
        @Required String device,
        @Required String action,
        @Optional Map<String, Object> params,
        @Optional String correlationId) {

    @Execute
    public Result run(DeviceCommandDispatcher dispatcher,
                      PluginExecutionContext executionContext) {
        String corrId = correlationId != null
                ? correlationId : UUID.randomUUID().toString();
        String execId = executionContext != null
                ? executionContext.executionId() : null;

        CommandResult result;
        try {
            result = dispatcher.dispatch(device, action, params, corrId, execId);
        } catch (IllegalArgumentException e) {
            return Result.failed(e.getMessage());
        }

        return switch (result) {
            case SENT -> Result.of(Map.of(
                    "result", "SENT",
                    "device", device,
                    "action", action,
                    "correlationId", corrId));
            case FAILED -> Result.failed(
                    "Command " + action + " failed for device " + device);
            case TIMEOUT -> Result.failed(
                    "Command " + action + " timed out for device " + device);
        };
    }
}
```

The annotation processor will generate:

```java
return spec.run(
    services.lookup(DeviceCommandDispatcher.class),
    services.lookup(PluginExecutionContext.class));
```

#### DeviceCommandDispatcher — accept executionId, fire binding events

```java
@ApplicationScoped
public class DeviceCommandDispatcher {

    private final DeviceRegistry registry;
    private final Map<String, DeviceProvider> providers;
    private final Consumer<PlaybookBindingEvent> bindingEvent;
    private final String tenancyId;

    @Inject
    public DeviceCommandDispatcher(
            DeviceRegistry registry,
            @Any Instance<DeviceProvider> providerBeans,
            @ConfigProperty(name = "casehub.iot.tenancy-id") String tenancyId,
            jakarta.enterprise.event.Event<PlaybookBindingEvent> bindingEvent) {
        this.registry = registry;
        this.providers = new HashMap<>();
        providerBeans.forEach(p -> providers.put(p.providerId(), p));
        this.tenancyId = tenancyId;
        this.bindingEvent = bindingEvent::fire;
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
            throw new IllegalArgumentException(
                    "No provider for: " + entity.providerId());
        }

        var command = new DeviceCommand(
                deviceId, action, params != null ? params : Map.of(),
                "iot-scenario", correlationId);

        if (executionId != null) {
            bindingEvent.accept(new PlaybookBindingEvent.StepStart(
                    executionId, tenancyId,
                    "iot.command:" + action,
                    Set.of(deviceId)));
        }

        CommandResult result = provider.dispatch(command);

        if (executionId != null) {
            switch (result) {
                case SENT -> {
                    bindingEvent.accept(
                            new PlaybookBindingEvent.DeviceProvisioned(
                                    executionId, tenancyId,
                                    "iot.command:" + action, deviceId));
                    bindingEvent.accept(
                            new PlaybookBindingEvent.StepComplete(
                                    executionId, tenancyId,
                                    "iot.command:" + action, 1, 0));
                }
                case FAILED, TIMEOUT -> {
                    String reason = result == CommandResult.TIMEOUT
                            ? "Command timed out" : "Command failed";
                    bindingEvent.accept(
                            new PlaybookBindingEvent.DeviceFailed(
                                    executionId, tenancyId,
                                    "iot.command:" + action,
                                    deviceId, reason));
                    bindingEvent.accept(
                            new PlaybookBindingEvent.StepFailed(
                                    executionId, tenancyId,
                                    "iot.command:" + action,
                                    0, 1, List.of(deviceId + ": " + reason)));
                }
            }
        }

        return result;
    }

    // Backward-compatible overload for non-scenario callers
    public CommandResult dispatch(String deviceId, String action,
                                  Map<String, Object> params,
                                  String correlationId) {
        return dispatch(deviceId, action, params, correlationId, null);
    }

    public Optional<DeviceEntity> findDevice(String deviceId) {
        return registry.findById(deviceId);
    }
}
```

**Synchronous event firing** — same rationale as DesiredStateDeliveryHandler: command dispatch is already I/O-bound (network call to device provider), and binding events must maintain lifecycle ordering (StepStart before StepComplete).

**stepName convention:** `"iot.command:" + action` (e.g., `"iot.command:set_temperature"`) — distinguishes command binding events from desired-state binding events in logs and debugging. The topology binder and frontend handle both identically.

### Layer 3: DesiredStateDeliveryHandler Unification

`DesiredStateDeliveryHandler.execute()` stops generating its own UUID and reads from `DeliveryContext.executionId()`:

```java
@Override
public StepOutcome execute(String stepName, Map<String, Object> data,
                           DeliveryContext ctx) {
    String executionId = ctx.executionId() != null
            ? ctx.executionId()
            : UUID.randomUUID().toString();  // fallback for non-playbook callers
    // ... rest unchanged, uses executionId as before
}
```

This is a one-line change. All downstream code (`reconcile()`, binding events) already uses the `executionId` variable.

### No Frontend Changes

The `PlaybookTopologyBinder` and the frontend topology components already handle `PlaybookBindingEvent` → SSE → highlight map. No new event types, no new SSE operations, no frontend work. Command binding "just works" once the events fire.

## Module Impact

| Module | Changes |
|--------|---------|
| `casehub-platform-yaml-plugin-api` (upstream) | New `PluginExecutionContext` interface |
| `casehub-pages-playbook` (upstream) | `DeliveryContext.executionId()` default method |
| `casehub-pages-playbook-runtime` (upstream) | `RuntimeDeliveryContext` carries executionId; `PlaybookExecutor` generates it per step and populates both `ServiceRegistry` and `DeliveryContext` |
| `scenario` | `IoTCommandPlugin` accepts `PluginExecutionContext` parameter; `DeviceCommandDispatcher` accepts executionId, fires `PlaybookBindingEvent` |
| `scenario` | `DesiredStateDeliveryHandler` reads `ctx.executionId()` instead of generating UUID |
| `testing` | Updated tests for new dispatcher signature and binding event verification |

## Boundary Rules

- `PluginExecutionContext` is a platform-level SPI — any domain plugin can use it
- `DeliveryContext.executionId()` is backward-compatible (default returns null)
- `DeviceCommandDispatcher` backward-compatible overload preserves existing callers (MCP, REST API, bridge)
- No changes to `PlaybookBindingEvent`, `PlaybookTopologyBinder`, or frontend components

## Testing Strategy

**Unit tests:**
- `DeviceCommandDispatcher` — verify binding events fire with executionId for SENT/FAILED/TIMEOUT results. Verify no events fire when executionId is null.
- `IoTCommandPlugin` — verify PluginExecutionContext is passed through to dispatcher. Verify null context handling.
- `DesiredStateDeliveryHandler` — verify `ctx.executionId()` is used when present, UUID fallback when absent.
- `RuntimeDeliveryContext` — verify executionId is returned.

**Integration tests:**
- Full command binding flow: fire `iot.command` step with executionId → verify SSE stream receives binding operations.
- Full desired-state binding flow: verify existing behavior unchanged with new executionId source.

## Implementation Note: Plugin Execution Path

The `iot.command` plugin may execute through two paths:

1. **Local delivery** — `PlaybookExecutor` → `DeliveryHandler` chain → eventually calls `Action.execute()` via `PluginRegistry`. The runtime populates `ServiceRegistry` with `PluginExecutionContext` before the call.
2. **Remote dispatch** — `PlaybookOrchestrator` → push to `PlaybookExecutorClient` → `ActionRegistry.invoke()` via `@PlaybookAction`. The `executionId` would need to flow through the dispatch protocol (JSON message) and be extracted by the executor client.

Both paths need the executionId, but the mechanism differs. During implementation planning, verify which path the IoT webapp actually uses for `iot.command` steps. If remote dispatch, the orchestrator's dispatch message format and `PlaybookExecutorClient.executeStep()` also need changes to thread executionId.

## Upstream Change Sequence

1. PR to `casehub-platform-yaml-plugin-api`: add `PluginExecutionContext`
2. PR to `casehub-pages-playbook`: add `DeliveryContext.executionId()` default method
3. PR to `casehub-pages-playbook-runtime`: generate executionId, populate `RuntimeDeliveryContext` and `ServiceRegistry`
4. `mvn install` in platform and pages to publish SNAPSHOTs
5. IoT changes: `IoTCommandPlugin`, `DeviceCommandDispatcher`, `DesiredStateDeliveryHandler`

Steps 1-3 are upstream changes that require approval before execution.

## References

- `IoTCommandPlugin.java:15` — current plugin without execution context
- `DeviceCommandDispatcher.java:38-56` — current dispatch method without executionId
- `DesiredStateDeliveryHandler.java:91` — current UUID generation
- `Action.java:6-13` (platform yaml-plugin-api) — plugin execution contract
- `ServiceRegistry.java:3-5` (platform yaml-plugin-api) — dependency injection mechanism
- `DeliveryContext.java:1-9` (pages-playbook) — delivery handler context interface
- `RuntimeDeliveryContext.java:7-42` (pages-playbook-runtime) — runtime context implementation
- `PlaybookExecutor.java:90-123` (pages-playbook-runtime) — step execution with delivery handler dispatch
- `PlaybookTopologyBinder.java` (webapp) — existing binding observer (unchanged)
- `PlaybookBindingEvent` (casehub-iot-api) — existing binding event sealed interface (unchanged)
- `IoTCommandPluginAction.java` (generated) — shows ServiceRegistry.lookup() pattern
- casehubio/iot#129 — scenario-topology binding (parent, deferred command binding to this issue)
- casehubio/iot#121 — IoT desired state epic (grandparent)
- `docs/specs/issue-129-scenario-topology-binding/2026-10-06-scenario-topology-binding-design.md` — parent spec that deferred command binding

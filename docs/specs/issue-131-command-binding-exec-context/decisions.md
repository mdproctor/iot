## D1: Blocker resolution strategy

**Choice:** Make upstream changes to yaml-plugin-api in the platform repo as part of this issue
**Alternatives:**
- IoT-only workaround — wrapping DeviceCommandDispatcher locally. Avoids cross-repo changes but doesn't solve it for other plugin consumers.
- Defer until upstream lands — blocks #131 indefinitely on someone else's timeline.
**Rationale:** The ServiceRegistry.lookup() mechanism means the IoT plugin just adds a new @Execute parameter — minimal API surface change. The fix belongs at the platform level since other domains will need execution context too.
**Trade-offs:** Requires cross-repo PR to platform. Must coordinate merge order.
**Sources:** `Action.java` (platform yaml-plugin-api), `IoTCommandPluginAction.java` (generated)
**Exploration:** quick
**Status:** captured

## D2: Execution context threading mechanism

**Choice:** ServiceRegistry injection — add `PluginExecutionContext` interface to yaml-plugin-api, runtime populates it before calling `Action.execute()`
**Alternatives:**
- Extend Action interface with a third parameter — breaks custom Action implementations, even with default method it changes the fundamental contract.
- DeliveryContext threading — couples two separate SPIs (Plugin and DeliveryHandler).
**Rationale:** Plugin authors opt in by adding `PluginExecutionContext` as an `@Execute` parameter. The annotation processor generates `services.lookup(PluginExecutionContext.class)`. Zero API breakage, fully opt-in.
**Trade-offs:** Runtime must populate the context before calling `Action.execute()`. Plugins that don't need it never see it.
**Sources:** `ServiceRegistry.java`, `IoTCommandPluginAction.java` (generated code shows lookup pattern)
**Exploration:** quick
**Status:** captured

## D3: PluginExecutionContext placement

**Choice:** Define in `casehub-platform-yaml-plugin-api` alongside ServiceRegistry and @Execute
**Alternatives:**
- pages-playbook — wrong level; Plugin SPI shouldn't depend on the playbook module.
- casehub-iot-api — wrong scope; other domains would need their own copies.
**Rationale:** Natural home — consumed by plugins, resolved via ServiceRegistry.lookup(), same module as the injection mechanism.
**Trade-offs:** Platform-level change requires platform repo PR.
**Sources:** `yaml-plugin-api` module structure
**Exploration:** quick
**Status:** captured

## D4: Event firing mechanism for commands

**Choice:** DeviceCommandDispatcher fires PlaybookBindingEvent before/after dispatch
**Alternatives:**
- Plugin returns metadata + observer correlates — requires audit event changes and two-step correlation.
- Callback via PluginExecutionContext — adds method to the platform SPI that's IoT-specific.
**Rationale:** DeviceCommandDispatcher is already a CDI bean. Adding executionId as a parameter and injecting `Event<PlaybookBindingEvent>` is minimal. The plugin just passes executionId through — no CDI access needed in the record.
**Trade-offs:** DeviceCommandDispatcher gains a dependency on PlaybookBindingEvent (casehub-iot-api). This is an IoT-internal dependency, not cross-module.
**Sources:** `DeviceCommandDispatcher.java:38-56`, `DesiredStateDeliveryHandler.java:162-208` (existing pattern)
**Exploration:** quick
**Status:** captured

## D5: Binding event model

**Choice:** Reuse existing PlaybookBindingEvent types for command binding
**Alternatives:**
- New CommandBindingEvent sealed interface — more precise semantics but duplicates SSE handling and frontend state machine.
- Extend PlaybookBindingEvent with command-specific variants — adds complexity to one hierarchy.
**Rationale:** Commands are single-device operations. `StepStart(deviceIds={deviceId})` + `StepComplete/StepFailed` after dispatch maps naturally. Same SSE path, same frontend highlighting. No new event types, no new SSE operations.
**Trade-offs:** Command semantics (SENT/TIMEOUT/FAILED) are coerced into desired-state semantics (provisioned/failed). Timeout maps to failed, which is accurate enough for visual binding.
**Sources:** `PlaybookBindingEvent` sealed interface, `PlaybookTopologyBinder.java`
**Exploration:** quick
**Status:** captured

## D6: ExecutionId source unification

**Choice:** Both Plugin and DeliveryHandler paths use a runtime-generated executionId. Add `executionId()` to `DeliveryContext` alongside the new `PluginExecutionContext` in yaml-plugin-api.
**Alternatives:**
- Keep DesiredStateDeliveryHandler generating its own UUID — two different identity sources, inconsistent when debugging or correlating events.
**Rationale:** Single execution identity source means all binding events from a given step share the same executionId regardless of delivery mechanism. The runtime generates it once per step.
**Trade-offs:** Requires adding `executionId()` to `DeliveryContext` (pages-playbook module) — another upstream change. DesiredStateDeliveryHandler must be updated to use `ctx.executionId()` instead of `UUID.randomUUID()`.
**Depends on:** D2 (ServiceRegistry injection), D3 (PluginExecutionContext placement)
**Sources:** `DeliveryContext.java`, `RuntimeDeliveryContext.java`, `DesiredStateDeliveryHandler.java:91`
**Exploration:** quick
**Status:** captured

# Playbook UI Evaluation for IoT Scenario Editing

**Issue:** casehubio/iot#128
**Branch:** issue-128-evaluate-playbook-ui
**Status:** Evaluation Complete

## Summary

The playbook UI is a **platform component** in `casehub-pages`, not fsitrading-specific. Fsitrading uses the same platform components with trading-domain step definitions. The IoT webapp already has these components available via Maven SNAPSHOT (`casehub-pages-npm` unpacked to `.casehub-packages/`) but hasn't wired them up.

**Verdict: Adapt, don't lift.** No code needs to be extracted from fsitrading. The platform provides the UI framework; IoT needs thin domain-specific layers on top.

## Platform Components Available

### Playbook Execution Controller (pages-aria)

| Component | File | Capability |
|-----------|------|-----------|
| `PagesPlaybookController` | `packages/pages-aria/src/controller/playbook-controller.ts` | Step outline tree, transport controls (play/pause/step/stop), speed slider, keyboard shortcuts (space=play/pause, →=step), run-to-step |
| `PlaybookConnectionController` | `packages/pages-aria/src/controller/playbook-connection-controller.ts` | Reactive controller managing WebSocket push wire for live state sync (`scenario:state` topic) |
| `PagesPlaybookYamlViewer` | `packages/pages-aria/src/controller/playbook-yaml-viewer.ts` | Floating YAML viewer with syntax highlighting, step line mapping, auto-scroll to current step |
| `PagesPlaybookNarrative` | `packages/pages-aria/src/controller/playbook-narrative.ts` | Markdown narrative content viewer |
| `PagesLibraryView` | `packages/pages-aria/src/controller/library-view.ts` | Script library browser with categories |
| `PagesActionCatalog` | `packages/pages-aria/src/controller/step-catalog.ts` | Step type catalog with search and filtering |

### Visual YAML Builder (pages-builder)

| Component | File | Capability |
|-----------|------|-----------|
| `PagesBuilderShell` | `packages/pages-builder/src/shell/builder-shell.ts` | Main editor shell with dock layout, visual/source/split modes, bidirectional sync |
| `PagesBuilderTree` | `packages/pages-builder/src/tree/builder-tree.ts` | Outline tree with drag-and-drop, context menus, keyboard shortcuts |
| `PagesBuilderPalette` | `packages/pages-builder/src/palette/builder-palette.ts` | Component catalog with category filters, search, drag-to-canvas |
| `PagesCodeEditor` | `packages/pages-code-editor/src/pages-code-editor.ts` | CodeMirror 6 wrapper with YAML syntax highlighting, schema-driven autocomplete |

### YAML Core (yaml-core)

| Module | File | Capability |
|--------|------|-----------|
| Playbook schema | `packages/yaml-core/src/playbook-schema.ts` | Schema registry for client/server/domain playbooks |
| Variable expansion | `packages/yaml-core/src/expand.ts` | `${prefix.key}` resolution with fallbacks |
| Modules | `packages/yaml-core/src/expand.ts` | Parameterized reusable templates |
| Step catalog | `packages/yaml-core/src/step/` | Plugin registry with portability types |

### Already in IoT `.casehub-packages/` (Not Yet Wired)

- `orchestration-workbench` — execution monitor + timeline
- `swf-diagram`, `htn-diagram`, `casehub-diagram` — workflow visualizers
- `pages-code-editor` — CodeMirror YAML editor
- `pages-document` — CST-backed document facade

## IoT Backend (Already Complete)

| Component | Status | What it provides |
|-----------|--------|-----------------|
| `DesiredStateDeliveryHandler` | Complete | Executes `delivery: desired-state` steps with preset resolution, goal compilation, plan/provision cycle |
| `IoTCommandPlugin` | Complete | Executes `iot.command` steps via DeviceCommandDispatcher |
| `PlaybookTopologyBinder` | Complete | Observes binding events → SSE → topology highlight map |
| `DefaultIoTPresetApi` | Complete | REST: list presets, diff preview, apply |
| `IoTPushEndpoint` | Complete | WebSocket `/push` with topic-based pub/sub |
| `TopologyStreamEvent` SSE | Complete | Live device state + binding operation stream |
| Bundled scenarios | Complete | `META-INF/scenarios/` with IoT-specific playbooks |

## What fsitrading Provides (Reference Value Only)

Fsitrading's value is as a **reference implementation**, not a source of liftable components:

- **Playbook YAML examples** (`app/src/main/resources/playbooks/`) — state machines, modules, step definitions with typed schemas. Useful as patterns for IoT playbook authoring.
- **Step definitions** (`steps/trading-steps.yaml`) — 16 domain-specific step definitions showing the schema format IoT should follow.
- **Ops Centre layout** (`ops-centre.ts`) — `dockWorkbench()` example with multi-zone panel layout. Shows how to compose platform components into a domain-specific page.
- **Simulation profiles** (`simulation/profiles/`) — scenario profile patterns for test data generation.

## Gap Analysis

### What IoT Needs to Build

| Gap | Size | Description |
|-----|------|-------------|
| Scenario page | S | Wire `PagesPlaybookController` + `PagesPlaybookYamlViewer` as an IoT page via `hostPanel()`. Connect to IoT push endpoint. |
| IoT step catalog | S | Define step entries for `iot.command`, `iot.state`, `delivery: desired-state` in the `yaml-core` step catalog format. |
| Scenario CRUD API | S | REST/GraphQL endpoints for scenario list/create/edit/delete. Backend `ScriptRegistry` exists but isn't exposed via REST in the IoT webapp. |
| Preset composer | M | Device picker + state configuration form + composition UI. No platform equivalent — IoT-specific. |
| Diff visualization | S | Before/after state comparison panel for preset preview. `DefaultIoTPresetApi.diff()` exists but no frontend renderer. |
| Execution history | S | List past runs with outcomes. Requires new persistence + REST endpoints. |

### What IoT Already Has (No Work Needed)

- Scenario execution runtime (DesiredStateDeliveryHandler, IoTCommandPlugin)
- Live topology binding during execution
- Push WebSocket infrastructure
- SSE streaming for topology updates
- Preset resolution and application

## Recommendation

**Phase 1 (S/Low) — Wire up execution controller:**
Wire `PagesPlaybookController` as an IoT "Scenarios" page. Connect to the IoT push endpoint. This gives immediate value: operators can browse, run, and monitor bundled IoT scenarios with step-by-step visualization.

**Phase 2 (S/Med) — Step catalog + scenario CRUD:**
Define IoT step catalog entries. Expose ScriptRegistry via REST so the library view works. Enable creating/uploading new scenarios.

**Phase 3 (M/Med) — Visual editor + preset composer:**
Wire `PagesBuilderShell` for visual scenario authoring. Build the IoT-specific preset composer (device picker, state config, composition). This is the only genuinely new UI work.

## Follow-Up Issues

| Issue | Title | Scale | Deps |
|-------|-------|-------|------|
| New | Wire PagesPlaybookController as IoT Scenarios page | S | — |
| New | Define IoT step catalog entries for yaml-core | S | — |
| New | Expose ScriptRegistry via REST for scenario CRUD | S | — |
| New | IoT preset composer — device picker + state config UI | M | Preset API (exists) |
| New | Preset diff visualization panel | S | Preset API (exists) |

## References

- `packages/pages-aria/src/controller/playbook-controller.ts` — platform playbook controller
- `packages/pages-builder/src/shell/builder-shell.ts` — visual YAML builder shell
- `packages/yaml-core/src/playbook-schema.ts` — schema registry
- `fsitrading/app/src/main/webui/src/ops-centre.ts` — reference: dockWorkbench layout pattern
- `fsitrading/app/src/main/resources/playbooks/` — reference: domain playbook examples
- `fsitrading/app/src/main/resources/steps/trading-steps.yaml` — reference: step definition format
- `iot/webapp/src/main/java/io/casehub/iot/webapp/push/IoTPushEndpoint.java` — IoT push endpoint
- `iot/webapp/src/main/java/io/casehub/iot/webapp/app/service/DefaultIoTPresetApi.java` — preset REST API
- `iot/webapp/src/main/java/io/casehub/iot/webapp/app/service/PlaybookTopologyBinder.java` — topology binding
- `iot/docs/specs/issue-129-scenario-topology-binding/2026-10-06-scenario-topology-binding-design.md` — parent design
- casehubio/iot#121 — IoT desired state epic (parent)
- casehubio/iot#129 — scenario-topology binding (sibling)
- casehubio/iot#131 — command binding with execution context (sibling, completed)

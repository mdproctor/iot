# casehub-iot — Contributor Guide

> Internal architecture reference for platform builders extending casehub-iot — provider implementations, bridge internals, SPIs, and testing infrastructure.

**GitHub:** [casehubio/iot](https://github.com/casehubio/iot)

---

## Internal Architecture

### DeviceProvider SPI

The `DeviceProvider` CDI SPI is the provider contract. Four methods: `providerId()`, `discover() → List<DeviceEntity>`, `dispatch(DeviceCommand) → CommandResult`, and `status() → ProviderStatus`. Provider implementations are CDI `@ApplicationScoped` beans — auto-discovered. Discovery returns the full device inventory; dispatch sends a command to a specific device; status reports connection health.

All SPIs are **blocking** — designed for virtual threads per ADR-0005. No `Uni<>` return types in the SPI layer. Implementations that need async internals (e.g. BridgeDeviceProvider WebSocket) block at the method boundary.

Provider activation uses `@LookupIfProperty(name = "casehub.iot.<provider>.enabled", stringValue = "true")` — disabled providers are invisible to `Instance<DeviceProvider>`. All provider config properties must be `Optional<String>` to prevent SmallRye startup validation failure.

REST clients are created programmatically via `RestClientBuilder` (not `@RegisterRestClient`) — base URLs are resolved at runtime to support auto-discovery.

### Vendor Supplement Types

Provider modules extend common types only for fields that have no cross-vendor equivalent. Common interface first, supplement last resort.

**Home Assistant:** `HomeAssistantThermostat` (preset mode, swing mode, HVAC action), `HomeAssistantLight` (color mode, effect), `HomeAssistantLock` (lock state enum from HA).

**OpenHAB:** `OpenHabThermostat` (heating/cooling demand), `OpenHabLight` (HSB colour), `OpenHabRollershutter` (OH-specific cover with inverted position semantics).

---

## Full Module Details

| Module | Artifact | Contents |
|--------|----------|----------|
| `api` | `casehub-iot-api` | Core SPIs (`DeviceProvider`, `DeviceRegistry`, `DeviceStateHistoryProvider`), device class hierarchy, `StateChangeEvent`, `DeviceCommand`, `CommandResult`, `IoTCloudEventAdapter`, `IoTCommandAuditEvent`, enums (`DeviceClass`, `SensorType`, `ThermostatMode`, `ProviderStatus`), `CdiDeviceRegistry @ApplicationScoped`. Depends on `casehub-platform-api`. Jackson annotations for `DeviceTypeIdResolver` polymorphic serialization (iot#5). **Public API, semver discipline.** |
| `homeassistant` | `casehub-iot-homeassistant` | `HomeAssistantProvider @ApplicationScoped` — REST API + WebSocket event stream. `HomeAssistantEntityMapper` maps HA states to device hierarchy. `HomeAssistantWebSocketClient` for real-time state subscriptions with exponential backoff reconnection. `HomeAssistantRestClient` for service calls (command dispatch). Supplement types: `HomeAssistantThermostat`, `HomeAssistantLight`, `HomeAssistantLock`. Config via `@ConfigMapping`: url, token, tenancyId, reconnect params. |
| `openhab` | `casehub-iot-openhab` | `OpenHabProvider @ApplicationScoped` — REST API + SSE event stream. Layered Equipment+Thing discovery: `OpenHabEntityMapper` maps Equipment Groups (semantic model), `OpenHabThingResolver` maps Things via two-signal model (thing-type category + channel itemType inference). `OpenHabSseClient` 4-phase pipeline with dual cache layers and `ThingStatusInfoChangedEvent` tracking. `OpenHabDeviceBuilder` shared between paths. `OpenHabRestClient` for item commands. Supplement types: `OpenHabThermostat`, `OpenHabLight`, `OpenHabRollershutter`. Auth: token or basic auth via `OpenHabAuthHeadersFactory` (`ClientHeadersFactory`). Config via `@ConfigMapping`: url, token, optional basicAuth, tenancyId, reconnect params, coalescing window, `thingDiscoveryEnabled` (default true). |
| `testing` | `casehub-iot-testing` | `MockDeviceProvider`, `MockDeviceRegistry`, `Fixtures` (Java-built fixture devices), `DeviceFixtureLoader` (YAML fixture loading), `DeviceTypeHandler` SPI (16 handlers for all device types including vendor supplements), `StateChangeEventPublisher`. Test scope only — never a compile or runtime dependency. Provider modules use `<optional>true</optional>` for `DeviceTypeHandler` SPI compilation. |
| `bridge-persistence-memory` | `casehub-iot-bridge-persistence-memory` | In-memory bounded ring buffer `BridgeAuditStore` — `@Alternative @Priority(100)`, for Pi and test isolation. |
| `bridge-persistence-jpa` | `casehub-iot-bridge-persistence-jpa` | JPA `BridgeAuditStore` — durable audit persistence with JSONB message storage (iot#38). Flyway migrations, Testcontainers PostgreSQL for tests. Configurable `@Scheduled` purge job for audit data retention (iot#40). |
| `bridge` | `casehub-iot-bridge` | Local bridge agent — event relay with CDI filter chain, WebSocket client to bridge-server. Runs on-premises or at the edge; forwards `StateChangeEvent` to cloud consumers and relays commands back. Standalone Quarkus app. |
| `bridge-server` | `casehub-iot-bridge-server` | Cloud-side library: `BridgeDeviceProvider implements DeviceProvider` — remote (bridged) devices look local to cloud consumers via the `DeviceProvider` SPI. `DeviceTypeIdResolver` for compound type ID serialization. 6 deployment topologies: SaaS, hybrid, multi-site, constrained edge, dev, multiple consumers (iot#5). |
| `mcp` | `casehub-iot-mcp` | MCP tools for Quarkus MCP server — `iot_get_devices`, `iot_get_state`, `iot_send_command`. Library module — add with `quarkus-mcp-server-http` to any Quarkus app. Host-agnostic: injects `DeviceRegistry` and `Instance<DeviceProvider>`. |
| `webapp-api` | `casehub-iot-webapp-api` | Reusable IoT JavaSwitch ganglia, case descriptors, worker functions, ActionRiskClassifier, `DismissalGangliaObserver`, REST interfaces, AI resolution data records (`AiResolutionPlan`, `AiResolutionPromptBuilder`). Tier 1 — no JPA, no Quarkus runtime. |
| `webapp-drools` | `casehub-iot-webapp-drools` | DroolsCEP temporal pattern ganglia (`SustainedTemperatureRiseRule`, `MultiRoomMotionRule`). Activates by classpath presence. |
| `webapp` | `casehub-iot-webapp` | Standalone Quarkus app — operational console with RAS situational awareness, case orchestration, REST API, SSE, TypeScript pages via Quinoa, `IoTAiResolutionAgent` (LLM resolution via `@Scheduled` polling). Three-datasource Flyway layout. |

---

## Provider Architecture

### Home Assistant

1:1 entity mapping — each HA `entity_id` maps to one `DeviceEntity`. Discovery via REST `GET /api/states`. Real-time via WebSocket: auth handshake → `subscribe_events` for `state_changed`. Reconnect with exponential backoff + jitter. Command dispatch via `POST /api/services/{domain}/{service}`.

### OpenHAB

Equipment Group mapping — one OpenHAB Equipment Group with multiple member Point items maps to a single `DeviceEntity`. Members are resolved by semantic tags (e.g. `Measurement+Temperature` → current temperature, `Control+Switch` → on/off state, `Setpoint+Temperature` → target temperature). Discovery via REST `GET /rest/items?type=Equipment&recursive=true`. Real-time via SSE `/rest/events` with Equipment-level coalescing — individual item state changes are resolved to their parent Equipment, re-mapped, and emitted as a single `StateChangeEvent` after a configurable coalescing window (default 50ms). Command dispatch via `POST /rest/items/{itemName}` — the target item is resolved from semantic tags matching the command action.

Auth: Bearer token (default) or HTTP Basic auth. Basic auth uses a CDI `ClientHeadersFactory` registered via `@RegisterProvider` on the REST clients.

### Thing-Scoped Discovery (Layered Equipment + Thing)

OpenHAB discovery operates in two layers. Phase 1 discovers Equipment Groups (semantic model). Phase 2 discovers Things directly via `OpenHabThingResolver` — resolves `OpenHabThingDto` and its linked items to `ResolvedDeviceFields` using a two-signal model: thing-type category (binding metadata) merged with channel itemType inference. Priority-based channel scanning (Color > Dimmer > Rollershutter > Player > Power/Energy > Thermostat > Temperature > Humidity > Switch > Contact > Number). `OpenHabDeviceBuilder` is shared between Equipment and Thing paths. `OpenHabSseClient.connect()` runs a 4-phase pipeline: Equipment mapping → Thing index build → Thing mapping for unmapped Things (`thingDiscoveryEnabled()` config, default `true`) → item state fetch for unmapped Things. Dual cache layers: `equipmentCache`/`deviceCache` (Equipment path) and `thingCache`/`thingDeviceCache` (Thing path). SSE `ThingStatusInfoChangedEvent` updates availability on both layers.

---

## Bridge Deployment

**Docker:** `bridge/src/main/docker/Dockerfile.jvm` — based on `eclipse-temurin:21-jre-alpine`, non-root user (UID 1001), Quarkus app layout, port 8080. `bridge/docker-compose.yml` — single-service compose (image `ghcr.io/casehubio/iot-bridge:latest`, host network, env_file `.env`, volume mount for event persistence, health check via `/q/health/ready`).

**Deployment guide:** `bridge/DEPLOYMENT.md` — architecture diagram, prerequisites, quick start, full configuration reference, network requirements, data persistence, updating, troubleshooting, security considerations, multi-platform support (amd64, arm64).

---

## CBR Infrastructure

IoT situation resolution via case-based reasoning. Implemented across `webapp-api` (pure logic) and `webapp` (CDI wiring).

**Feature schemas** (`IoTCbrFeatureSchemas`): 4 `CbrFeatureSchema` instances — `hvacAnomaly()`, `safetyAlert()`, `securityAlert()`, `genericResponse()`. Each includes common fields (deviceClass, roomType, hourOfDay, dayType, season) plus schema-specific fields.

**Retrieval** (`IoTCbrRetrievalService`): wraps `CbrCaseMemoryStore`, builds `CbrQuery`, returns `List<ResolutionSuggestion>` with `caseId`, `similarityScore`, `problem`, `solution`, `outcome`, `confidence`, `matchedFeatures`, `featureSimilarities`, `planSteps`.

**Feature extractors** (`IoTCbrFeatureExtractors`): static extractors per case type — `extractHvacAnomalyFeatures`, `extractSafetyAlertFeatures`, `extractSecurityAlertFeatures`, `extractGenericResponseFeatures`. Derives temporal features from `eventTimestamp`.

**Confidence model** (`ResolutionConfidence`): `bestSimilarity`, `outcomeConsistency`, `matchCount`, `ConfidenceLevel` (HIGH/MEDIUM/LOW/NONE). Static `compute()` method.

**CDI wiring:** `IoTCbrSchemaRegistration` (`@ApplicationScoped`) registers all schemas on `@Observes StartupEvent`. `IoTCbrRetrievalServiceProducer` CDI `@Produces` method.

**REST:** `GET /api/cases/{caseId}/suggestions` retrieves CBR suggestions. `POST /api/cases/{caseId}/suggestions/{pastCaseId}/accept` accepts a suggestion.

---

## Configuration

### Home Assistant (`casehub.iot.ha.*`)

| Property | Required | Default | Purpose |
|----------|----------|---------|---------|
| `casehub.iot.ha.url` | yes | — | HA instance URL |
| `casehub.iot.ha.token` | yes | — | Long-lived access token |
| `casehub.iot.ha.tenancy-id` | yes | — | Multi-tenant isolation key |
| `casehub.iot.ha.reconnect-base-seconds` | no | `5` | Backoff base |
| `casehub.iot.ha.reconnect-max-seconds` | no | `300` | Backoff cap |
| `casehub.iot.ha.ping-interval-seconds` | no | `30` | WebSocket keep-alive |
| `casehub.iot.ha.pong-timeout-seconds` | no | `10` | Pong deadline |

### OpenHAB (`casehub.iot.openhab.*`)

| Property | Required | Default | Purpose |
|----------|----------|---------|---------|
| `casehub.iot.openhab.url` | yes | — | OpenHAB instance URL |
| `casehub.iot.openhab.token` | yes (unless basic auth) | — | API token |
| `casehub.iot.openhab.basic-auth.username` | no | — | Basic auth username |
| `casehub.iot.openhab.basic-auth.password` | no | — | Basic auth password |
| `casehub.iot.openhab.tenancy-id` | yes | — | Multi-tenant isolation key |
| `casehub.iot.openhab.reconnect-base-seconds` | no | `5` | Backoff base |
| `casehub.iot.openhab.reconnect-max-seconds` | no | `300` | Backoff cap |
| `casehub.iot.openhab.coalesce-window-ms` | no | `50` | SSE event coalescing window |
| `casehub.iot.openhab.thing-discovery-enabled` | no | `true` | Enable Thing-scoped discovery |

### Tenancy

Single tenancy property: `casehub.iot.tenancy-id` — never per-module `tenancyId()` in `@ConfigMapping`.

---

## Depended On By

| Repo | Module | What it uses |
|------|--------|-------------|
| `casehub-life` | `app` | Device discovery, state events, command dispatch for household automation |
| `casehub-ops` | `iot` | IoT desired-state domain implementation (research) |

---

## Current State

- All SPIs are blocking (virtual-thread-aligned per ADR-0005)
- `DeviceEntity.location()` is nullable — populated by OpenHAB (from `thing.location()`), null for HA (area registry integration pending)
- Device class vocabulary aligned with Matter Device Type Library
- Jackson annotations on `api` for `DeviceTypeIdResolver` polymorphic serialization (iot#5) — `api` is no longer a zero-framework-dependency module
- Device metadata flows into case working layer via `IoTCaseInputContributor` — CDI implementation of `CaseInputContributor` SPI (`casehub-ras-api`)
- Docker image: `ghcr.io/casehubio/iot-bridge` (JVM, multi-arch ARM64+x86_64)

---

## Design Documents

- **Design spec:** `docs/superpowers/specs/2026-06-05-iot-foundation-design.md`
- **Research:** Available in `casehubio/parent` — `docs/superpowers/research/2026-06-05-home-automation-research.md`
- **Bridge deployment:** `bridge/DEPLOYMENT.md`
- **ADRs:** `docs/adr/`

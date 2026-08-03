# casehub-iot — Consumer Guide

> Typed IoT device abstraction layer for the CaseHub ecosystem — Matter-aligned device classes, reactive discovery, command dispatch, and CDI state change events.

**GitHub:** [casehubio/iot](https://github.com/casehubio/iot)
**Tier:** Foundation (consumed by application-tier repos)

---

## Purpose

Provides a unified device model regardless of the underlying home automation platform. Application repos consume the `api` module and receive typed device entities, state change events, and command dispatch — without coupling to Home Assistant, OpenHAB, or any specific provider.

`api` is a **public API surface — semver discipline applies from first release.** Community automations in casehub-life and downstream depend on it.

---

## Module Structure

Consumer-relevant modules — what to depend on and why:

| Module | Artifact | When to use |
|--------|----------|-------------|
| `api` | `casehub-iot-api` | Always. Core SPIs, device class hierarchy, `StateChangeEvent`, `DeviceCommand`, `CommandResult`, enums. |
| `bridge-server` | `casehub-iot-bridge-server` | Cloud apps consuming remote (bridged) devices. `BridgeDeviceProvider implements DeviceProvider` — remote devices look local. |
| `mcp` | `casehub-iot-mcp` | LLM agent device access. Add with `quarkus-mcp-server-http` for `iot_get_devices`, `iot_get_state`, `iot_send_command` tools. |
| `testing` | `casehub-iot-testing` | Test scope only. `MockDeviceProvider`, `MockDeviceRegistry`, fixture devices (Java + YAML), `StateChangeEventPublisher`. |

---

## Device Class Hierarchy

`DeviceEntity` is the abstract root. All device types extend it with domain-specific fields and a typed `Builder`. Vocabulary aligned with the Matter Device Type Library.

| Type | Class | Key Fields |
|------|-------|------------|
| `SwitchDevice` | `SWITCH` | `on` |
| `LightDevice` | `LIGHT` | `on`, `brightness` |
| `ThermostatDevice` | `THERMOSTAT` | `currentTemperature`, `targetTemperature`, `mode` |
| `SensorDevice` | `SENSOR` | `sensorType`, `numericValue`, `unit` |
| `PresenceSensor` | `PRESENCE_SENSOR` | `present`, `lastSeen` |
| `PowerSensor` | `POWER_SENSOR` | `power`, `energy`, `voltage`, `current` |
| `LockDevice` | `LOCK` | `locked` |
| `CoverDevice` | `COVER` | `position`, `moving` |
| `MediaPlayerDevice` | `MEDIA_PLAYER` | `volume` |
| `FanDevice` | `FAN` | `on` |
| `CameraDevice` | `CAMERA` | `streaming` |

Every `DeviceEntity` carries `deviceId`, `deviceClass`, `label`, `available`, `lastUpdated`, and `tenancyId`. The `capabilities()` method returns a `Map<String, Object>` used by `StateChangeEvent.deriveChangedCapabilities()` to compute change sets.

---

## DeviceRegistry SPI (Consumer Contract)

The `DeviceRegistry` CDI SPI is the primary consumer interface. Methods: `findById(String)`, `findByClass(Class<T>)`, `findByTenancyId(String)`, `findAll()`, `refresh()`. `CdiDeviceRegistry` is the `@ApplicationScoped` default — aggregates all `DeviceProvider` beans and delegates.

All SPIs are **blocking** — designed for virtual threads per ADR-0005. No `Uni<>` return types in the SPI layer.

---

## StateChangeEvent

CDI async event fired when a device's state changes. Carries `before`, `after`, `changedCapabilities` set, `timestamp`, and `providerId`. Consumers must use `@ObservesAsync`. The `deriveChangedCapabilities()` static method compares `capabilities()` maps to produce the diff.

---

## DeviceCommand

Immutable command record: `targetDeviceId`, `action`, `parameters`, `source`, `correlationId`. Static factory methods for common actions: `turnOn`, `turnOff`, `setTemperature`, `lock`, `unlock`, `setPosition`, `setVolume`. Action constants: `ACTION_TURN_ON`, `ACTION_TURN_OFF`, `ACTION_SET_TEMPERATURE`, `ACTION_LOCK`, `ACTION_UNLOCK`, `ACTION_SET_POSITION`, `ACTION_SET_VOLUME`.

---

## SSE Device Status Streaming

`DeviceSseResource` (`GET /api/devices/stream`) produces `SERVER_SENT_EVENTS`. Sends initial "snapshot" operation with all devices, then streams "replace" operations on state changes. Filters by tenancy ID.

---

## MCP Tools

The `mcp` module provides three tools for LLM agent integration:

- `iot_get_devices` — list devices visible to the host app
- `iot_get_state` — get current state of a specific device
- `iot_send_command` — send a command to a device

Host-agnostic: injects `DeviceRegistry` and `Instance<DeviceProvider>`, sees whatever providers the host app configures.

---

## Testing Infrastructure

The `testing` module (test scope only) provides:

- **`MockDeviceProvider` / `MockDeviceRegistry`** — CDI mocks for unit and integration tests
- **`Fixtures`** — static factory methods for every device type (`Fixtures.light()`, `Fixtures.thermostat()`, etc.)
- **`DeviceFixtureLoader`** — YAML fixture loading via `DeviceTypeHandler` SPI (16 handlers for all device types including vendor supplements)
- **`StateChangeEventPublisher`** — fires `StateChangeEvent` via CDI `fireAsync()` for `@QuarkusTest` integration tests

---

## Dependencies

`casehub-iot-api` depends on `casehub-platform-api`. Provider modules depend on Quarkus REST Client, Jackson, and WebSocket/SSE extensions. CBR features (webapp) depend on `casehub-neocortex` for `CbrCaseMemoryStore`, `CbrFeatureSchema`, `FeatureField`, `SimilaritySpec`.

---

## What It Does NOT Do

- **No domain logic.** IoT provides device abstraction, not automation rules. Business logic belongs in consuming repos (casehub-life, casehub-ops).
- **No direct provider coupling.** Consumers never import `homeassistant` or `openhab` modules — they depend on `api` and receive providers via CDI.
- **No persistence.** Device state is live from providers. Historical state tracking is the consumer's responsibility.
- **No UI framework.** The webapp module is a standalone operational console, not a reusable UI component.

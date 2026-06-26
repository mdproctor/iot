# CaseHub IoT Bridge — Deployment Guide

The CaseHub IoT Bridge is a lightweight Quarkus application that runs locally on your network to connect Home Assistant or OpenHAB devices to the CaseHub cloud. It forwards device state changes and relays commands back to your local automation platform.

## Architecture

```
[Home Assistant / OpenHAB]
         ↓ (local network)
    [IoT Bridge]
         ↓ (WebSocket over TLS)
    [CaseHub Cloud]
```

The bridge:
- Connects to Home Assistant via WebSocket or OpenHAB via SSE
- Forwards `StateChangeEvent`s to the cloud
- Relays `DeviceCommand`s back to the local provider
- Persists events to disk during cloud disconnections
- Supports auto-discovery via mDNS and SSDP

## Prerequisites

1. **Docker** and **Docker Compose** installed
2. **Home Assistant** or **OpenHAB** running on your local network
3. **Access token** from your automation platform:
   - **Home Assistant:** Profile → Security → Long-Lived Access Tokens → Create Token
   - **OpenHAB:** Settings → API Security → Add API Token
4. **CaseHub cloud credentials** (bridge endpoint URL and authentication token)

## Quick Start

### 1. Create configuration

```bash
cd bridge/
cp .env.example .env
```

Edit `.env` and fill in:

```bash
# Required — Cloud connection
CASEHUB_IOT_TENANCY_ID=your-tenant-id
CASEHUB_IOT_BRIDGE_CLOUD_ENDPOINT=wss://cloud.casehub.io/iot/bridge
CASEHUB_IOT_BRIDGE_TOKEN=<your-bridge-auth-token>

# Required — Home Assistant
CASEHUB_IOT_HOMEASSISTANT_ENABLED=true
CASEHUB_IOT_HOMEASSISTANT_TOKEN=<your-ha-long-lived-access-token>

# Optional — OpenHAB
# CASEHUB_IOT_OPENHAB_ENABLED=true
# CASEHUB_IOT_OPENHAB_AUTH_BEARER_TOKEN=<your-openhab-api-token>
```

### 2. Deploy

```bash
docker compose up -d
```

### 3. Verify

Check health:

```bash
curl http://localhost:8080/q/health/ready
```

Expected output:

```json
{
  "status": "UP",
  "checks": [
    {"name": "HomeAssistant connection", "status": "UP"},
    {"name": "Cloud bridge connection", "status": "UP"}
  ]
}
```

Check logs:

```bash
docker compose logs -f bridge
```

You should see:

```
INFO  [io.casehub.iot.homeassistant.HomeAssistantWebSocketClient] Connected to Home Assistant at ws://homeassistant.local:8123/api/websocket
INFO  [io.casehub.iot.bridge.agent.BridgeWebSocketClient] Connected to cloud bridge at wss://cloud.casehub.io/iot/bridge
```

## Configuration Reference

### Bridge Agent — Cloud Connection

| Environment Variable | Default | Required | Description |
|---------------------|---------|----------|-------------|
| `CASEHUB_IOT_TENANCY_ID` | `default` | Yes | Tenancy ID for multi-tenant cloud deployments |
| `CASEHUB_IOT_BRIDGE_CLOUD_ENDPOINT` | — | Yes | WebSocket endpoint for cloud connection (e.g., `wss://cloud.casehub.io/iot/bridge`) |
| `CASEHUB_IOT_BRIDGE_TOKEN` | — | Yes | Authentication token for bridge → cloud connection |
| `CASEHUB_IOT_BRIDGE_RECONNECT_BASE_SECONDS` | `5` | No | Base delay for exponential backoff reconnect (seconds) |
| `CASEHUB_IOT_BRIDGE_RECONNECT_MAX_SECONDS` | `300` | No | Max delay for exponential backoff reconnect (seconds) |
| `CASEHUB_IOT_BRIDGE_HEARTBEAT_INTERVAL_SECONDS` | `30` | No | Heartbeat interval to keep connection alive (seconds) |

### Event Store — Persistent Queue

| Environment Variable | Default | Required | Description |
|---------------------|---------|----------|-------------|
| `CASEHUB_IOT_BRIDGE_EVENT_STORE_MAX_SIZE` | `10000` | No | Maximum events in persistent store before oldest are dropped |
| `CASEHUB_IOT_BRIDGE_EVENT_STORE_DIRECTORY` | `data/bridge-events` | No | Directory for event persistence (mounted as volume in Docker) |

### Home Assistant Provider

| Environment Variable | Default | Required | Description |
|---------------------|---------|----------|-------------|
| `CASEHUB_IOT_HOMEASSISTANT_ENABLED` | `false` | Yes¹ | Enable Home Assistant integration |
| `CASEHUB_IOT_HOMEASSISTANT_TOKEN` | — | Yes¹ | Long-lived access token from Home Assistant |
| `CASEHUB_IOT_HOMEASSISTANT_URL` | — | No² | Home Assistant base URL (e.g., `http://192.168.1.100:8123`) |
| `CASEHUB_IOT_HOMEASSISTANT_RECONNECT_BASE_SECONDS` | `5` | No | WebSocket reconnect base delay (seconds) |
| `CASEHUB_IOT_HOMEASSISTANT_RECONNECT_MAX_SECONDS` | `300` | No | WebSocket reconnect max delay (seconds) |
| `CASEHUB_IOT_HOMEASSISTANT_PING_INTERVAL_SECONDS` | `30` | No | WebSocket ping interval (seconds) |
| `CASEHUB_IOT_HOMEASSISTANT_PONG_TIMEOUT_SECONDS` | `10` | No | WebSocket pong timeout (seconds) |
| `CASEHUB_IOT_HOMEASSISTANT_DISCOVERY_TIMEOUT_SECONDS` | `5` | No | mDNS discovery timeout (seconds) |

¹ Required if Home Assistant is your primary automation platform  
² If omitted, auto-discovery via mDNS (`homeassistant.local`) is attempted

### OpenHAB Provider

| Environment Variable | Default | Required | Description |
|---------------------|---------|----------|-------------|
| `CASEHUB_IOT_OPENHAB_ENABLED` | `false` | Yes³ | Enable OpenHAB integration |
| `CASEHUB_IOT_OPENHAB_URL` | — | No⁴ | OpenHAB base URL (e.g., `http://192.168.1.101:8080`) |
| `CASEHUB_IOT_OPENHAB_AUTH_BEARER_TOKEN` | — | Yes³ | API token from OpenHAB (Settings → API Security) |
| `CASEHUB_IOT_OPENHAB_AUTH_BASIC_USERNAME` | — | No⁵ | Basic auth username (alternative to bearer token) |
| `CASEHUB_IOT_OPENHAB_AUTH_BASIC_PASSWORD` | — | No⁵ | Basic auth password (alternative to bearer token) |
| `CASEHUB_IOT_OPENHAB_RECONNECT_BASE_SECONDS` | `5` | No | SSE reconnect base delay (seconds) |
| `CASEHUB_IOT_OPENHAB_RECONNECT_MAX_SECONDS` | `300` | No | SSE reconnect max delay (seconds) |
| `CASEHUB_IOT_OPENHAB_COALESCE_WINDOW_MS` | `50` | No | Event coalescing window (milliseconds) |
| `CASEHUB_IOT_OPENHAB_THING_DISCOVERY_ENABLED` | `true` | No | Enable Thing-level discovery |
| `CASEHUB_IOT_OPENHAB_DISCOVERY_TIMEOUT_SECONDS` | `10` | No | mDNS/SSDP discovery timeout (seconds) |

³ Required if OpenHAB is your primary automation platform  
⁴ If omitted, auto-discovery via mDNS (`openhab.local`) and SSDP is attempted  
⁵ Required if using basic auth instead of bearer token (both must be set)

## Network Requirements

The bridge requires **host network mode** for mDNS and SSDP multicast discovery to work. This is already configured in `docker-compose.yml`:

```yaml
network_mode: host
```

**Consequence:** The bridge container shares the host's network namespace. Port 8080 must be available on the host.

**Firewall:** Ensure UDP ports 5353 (mDNS) and 1900 (SSDP) are open if using auto-discovery.

## Data Persistence

The event store persists events to disk during cloud disconnections. The `docker-compose.yml` mounts a volume:

```yaml
volumes:
  - ./data/bridge-events:/app/data/bridge-events
```

Events are stored as JSON files in this directory. The bridge automatically replays unsent events when the cloud connection is restored.

**Backup:** To preserve events across bridge reinstalls, back up the `./data/bridge-events/` directory.

## Updating

Pull the latest image and restart:

```bash
docker compose pull
docker compose up -d
```

The bridge gracefully shuts down, persisting any unsent events before the container stops.

## Troubleshooting

### Bridge starts but health check fails

**Symptom:**

```bash
curl http://localhost:8080/q/health/ready
```

Returns:

```json
{
  "status": "DOWN",
  "checks": [
    {"name": "HomeAssistant connection", "status": "DOWN"}
  ]
}
```

**Diagnosis:**

1. Check bridge logs:

   ```bash
   docker compose logs bridge | grep -i error
   ```

2. Common causes:
   - Invalid `CASEHUB_IOT_HOMEASSISTANT_TOKEN` → regenerate token in Home Assistant
   - Home Assistant not reachable → verify URL or mDNS resolution (`ping homeassistant.local`)
   - Network mode not set to `host` → check `docker-compose.yml`

### Auto-discovery fails

**Symptom:**

```
WARN  [io.casehub.iot.homeassistant.HomeAssistantMDnsDiscovery] mDNS discovery timeout — no Home Assistant instance found
```

**Solutions:**

1. **Set explicit URL:**

   ```bash
   CASEHUB_IOT_HOMEASSISTANT_URL=http://192.168.1.100:8123
   ```

2. **Verify mDNS works on host:**

   ```bash
   ping homeassistant.local
   ```

   If this fails, mDNS is not available. Use explicit URL instead.

3. **Check firewall:** Ensure UDP port 5353 is open.

### Cloud connection fails

**Symptom:**

```
ERROR [io.casehub.iot.bridge.agent.BridgeWebSocketClient] Failed to connect to cloud: 401 Unauthorized
```

**Solutions:**

1. Verify `CASEHUB_IOT_BRIDGE_TOKEN` matches the token issued by CaseHub cloud
2. Check `CASEHUB_IOT_BRIDGE_CLOUD_ENDPOINT` URL is correct
3. Verify `CASEHUB_IOT_TENANCY_ID` matches your cloud tenant

### Events not forwarded after reconnect

**Symptom:** Bridge logs show cloud connection restored, but events from before the disconnection are not replayed.

**Diagnosis:**

1. Check event store directory permissions:

   ```bash
   ls -la ./data/bridge-events/
   ```

2. Ensure the directory is writable by the container (UID 1001):

   ```bash
   chown -R 1001:1001 ./data/bridge-events/
   ```

3. Check event store logs:

   ```bash
   docker compose logs bridge | grep PersistentBridgeEventStore
   ```

### High memory usage

**Symptom:** Bridge container using excessive memory.

**Diagnosis:**

1. Check event store size:

   ```bash
   ls -lh ./data/bridge-events/
   ```

2. If the event store is near `CASEHUB_IOT_BRIDGE_EVENT_STORE_MAX_SIZE` (default: 10,000 events), the bridge is unable to forward events to the cloud. Investigate cloud connection issues first.

3. Reduce max size if necessary:

   ```bash
   CASEHUB_IOT_BRIDGE_EVENT_STORE_MAX_SIZE=5000
   ```

## Security Considerations

1. **Token security:** Store `.env` securely — it contains long-lived access tokens. Never commit `.env` to version control.

2. **Network exposure:** The bridge exposes port 8080 for health checks. This endpoint is read-only (no mutations), but consider restricting access via firewall if the host is internet-exposed.

3. **TLS:** The cloud WebSocket connection uses TLS (`wss://`). Local connections (Home Assistant, OpenHAB) are typically unencrypted (`http://`, `ws://`) because they run on a trusted local network.

4. **Token rotation:** Regenerate Home Assistant and OpenHAB tokens periodically. Update `.env` and restart:

   ```bash
   docker compose restart
   ```

## Multi-Platform Support

The Docker image is built for:

- **linux/amd64** (x86_64) — Intel/AMD servers, desktops
- **linux/arm64** (ARM64) — Raspberry Pi 4/5, Apple Silicon (via Docker Desktop), AWS Graviton

Docker automatically pulls the correct architecture. No configuration needed.

## Logs

View live logs:

```bash
docker compose logs -f bridge
```

Logs include:

- Provider connection state (HA, OpenHAB)
- Cloud connection state
- Event forwarding (audit events)
- Auto-discovery attempts
- Reconnection backoff

**Log levels:** Controlled via Quarkus logging config. To enable debug logs, add to `.env`:

```bash
QUARKUS_LOG_LEVEL=DEBUG
```

Restart to apply:

```bash
docker compose restart
```

## Uninstalling

Stop and remove the container:

```bash
docker compose down
```

Remove the image:

```bash
docker rmi ghcr.io/casehubio/iot-bridge:latest
```

Delete persisted events (optional):

```bash
rm -rf ./data/bridge-events/
```

## Support

- **Issues:** https://github.com/casehubio/iot/issues
- **Documentation:** https://docs.casehub.io/iot/bridge
- **CaseHub cloud status:** https://status.casehub.io

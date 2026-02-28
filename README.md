# MCStatsPlugin

Paper plugin that collects in-game statistics and sends them to the MCStats API in async batches.

## Features

- Paper + Folia compatible scheduler support
- Event collection for:
  - `joins`
  - `quits`
  - `deaths`
  - `play_time_seconds` (periodic online-player increments)
- Telemetry collection for:
  - `TPS`, `MSPT`
  - `CPU usage`, `RAM used/total`
  - `Disk read/write` (OS auto-detected)
  - `Network Rx/Tx` (OS auto-detected)
  - `GC collections per minute`, `thread count`
  - online players
  - ping percentiles (`p50`, `p95`, `p99`)
- Async batched delivery to `/v1/events/batch`
- Async batched delivery to `/v1/telemetry/batch`
- In-memory retry queue
- Required ingest auth headers (`server-id`, `api-key`, `timestamp`, `signature`, `idempotency-key`)
- Windows/Linux runtime metrics are auto-detected via OSHI.

## Configuration (`config.yml`)

```yaml
api:
  base-url: "http://127.0.0.1:5000"
  server-id: "paper-main-1"
  api-key: "replace-with-api-key"
  timeout-ms: 5000
  flush-interval-seconds: 5
  batch-size: 200
  telemetry-flush-interval-seconds: 10
  telemetry-batch-size: 120
  secret: "replace-with-hmac-secret"

collect:
  playtime-tick-seconds: 60
  telemetry-sample-seconds: 10
```

## Build

```bash
./gradlew build
```

## Required API endpoint

- `POST /v1/events/batch`
- `POST /v1/telemetry/batch`

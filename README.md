# MCStatsPlugin

Paper plugin that collects in-game statistics and sends them to the MCStats API in async batches.

## Features

- Event collection for:
  - `joins`
  - `quits`
  - `deaths`
  - `play_time_seconds` (periodic online-player increments)
- Async batched delivery to `/v1/events/batch`
- In-memory retry queue
- Optional HMAC signature headers

## Configuration (`config.yml`)

```yaml
api:
  base-url: "http://127.0.0.1:5000"
  timeout-ms: 5000
  flush-interval-seconds: 5
  batch-size: 200
  secret: ""

collect:
  playtime-tick-seconds: 60
```

## Build

```bash
./gradlew build
```

## Required API endpoint

- `POST /v1/events/batch`

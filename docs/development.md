# Development

## Running

### Everything in Docker

```bash
docker compose --profile full up -d --build
```

This needs a `.env` file: `cp .env.example .env`. The Postgres init script creates one database and role per service from the `*_DB_NAME`, `*_DB_USER` and `*_DB_PASSWORD` variables, and it fails if any of them are missing.

> The init script only runs on an empty data volume. If you change DB credentials later, reset with `docker compose down -v`.

### Infrastructure in Docker, services from the IDE

Without `--profile full`, Compose starts only Postgres, Kafka and Kafka UI:

```bash
docker compose up -d
```

Then build the libraries once and run a service:

```bash
./mvnw install -DskipTests
WAREHOUSE_DB_PASSWORD=warehouse ./mvnw -pl services/warehouse-service spring-boot:run
```

Services default to `localhost:5432` for Postgres and `localhost:29092` for Kafka (the broker's host listener). Run all four on different ports with `--server.port` or `SERVER_PORT`, since they all default to 8080 outside Docker.

### Ports

| Component             | Port               |
|-----------------------|--------------------|
| warehouse-service     | 8081               |
| inventory-service     | 8082               |
| reorder-service       | 8083               |
| notification-service  | 8084               |
| simulator             | 8085               |
| Kafka (host listener) | 29092              |
| Kafka UI              | 8090               |
| PostgreSQL            | 5432               |
| Prometheus            | 9090               |
| Grafana               | 3000 (admin/admin) |

## Configuration

Each service reads its database from `<SERVICE>_DB_HOST`, `_DB_PORT`, `_DB_NAME`, `_DB_USER` and `_DB_PASSWORD`. Docker Compose overrides them with `SPRING_DATASOURCE_*` and `SPRING_KAFKA_BOOTSTRAP_SERVERS`.

Library settings (outbox, idempotency, retry) are described in [kafka.md](kafka.md). Service-specific ones:

| Property                                         | Service      | Default |
|--------------------------------------------------|--------------|---------|
| `app.kafka.topics.auto-create`                   | all          | `true`  |
| `app.purchase-orders.ttl`                        | reorder      | `P7D`   |
| `app.purchase-orders.sweep-interval`             | reorder      | `PT1H`  |
| `app.notification.rules.bulk-movement.threshold` | notification | `500`   |

The demo simulator's settings are listed in [simulator.md](simulator.md#configuration).

## Testing

```bash
./mvnw verify
```

Integration tests use **Testcontainers** to start real PostgreSQL and Kafka containers, so Docker must be running. No embedded brokers or mocked Kafka are used. They cover, among other things:

- outbox publishing, behavior when Kafka is unavailable, and sweeping old rows
- duplicate deliveries being ignored by consumers
- retries, dead-lettering, and undeserializable records going to the DLT while the consumer keeps going
- reorder logic, including concurrent stock events for the same SKU

CI (GitHub Actions) runs `./mvnw verify` and builds the Docker image for each service on every push and pull request.

## Observability

Each service exposes `/actuator/health` (with liveness and readiness probes) and `/actuator/prometheus`. Prometheus scrapes all four, and Grafana loads the **Warehouse Stock Sync** dashboard on startup.

Custom metrics:

| Metric                                | Type                           | Meaning                                                                |
|---------------------------------------|--------------------------------|------------------------------------------------------------------------|
| `outbox.events.pending`               | gauge                          | Events recorded but not yet published                                  |
| `outbox.events.oldest.pending.age`    | gauge                          | Age of the oldest unpublished event. Growing means publishing is stuck |
| `outbox.events.published` / `.failed` | counter, tag `topic`           | Publish outcomes                                                       |
| `kafka.consumer.failed.deliveries`    | counter, tags `topic`, `group` | Failed processing attempts                                             |
| `kafka.consumer.dead.lettered`        | counter                        | Records sent to a DLT                                                  |
| `kafka.consumer.dead.letter.failures` | counter                        | Records that couldn't even be dead-lettered                            |

Spring Kafka listener and template observations are on, so processing latency histograms (`spring.kafka.listener`, `spring.kafka.template`) appear in the dashboard too.

### Inspecting Kafka

Kafka UI at http://localhost:8090 shows topics, messages with their `__TypeId__` header, consumer group offsets and lag, and DLT contents. To check whether anything was dead-lettered, look for topics ending in `.dlt`.

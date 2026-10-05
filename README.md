# order-events-platform

Event-driven order processing with Spring Boot 3, Kafka and PostgreSQL.

**Author:** Khudsiya Syeda · **License:** MIT

> Representative portfolio project built independently with synthetic data; not derived from any employer or client codebase.

## Business use case

An online retailer needs to accept orders quickly and reliably while letting downstream teams (customer notifications, fulfilment, analytics) react to new orders without being coupled to the ordering API. The `order-service` validates and persists each order, then publishes an `OrderCreated` event to Kafka. The `notification-service` consumes those events and sends an order confirmation. Each event is processed exactly once even when Kafka redelivers it. Failures are retried, and messages that still can't be processed go to a dead-letter topic for triage instead of blocking the stream.

## Features

- **REST API** for placing and querying orders. Requests are checked with Bean Validation, and errors come back as RFC 7807 `ProblemDetail` JSON (`application/problem+json`).
- **Spring Data JPA + Flyway**: versioned, portable migrations and synthetic seed data. Hibernate never changes the schema (`ddl-auto: none`).
- **Events are published only after commit** (`@TransactionalEventListener(AFTER_COMMIT)`), so an order that rolls back never produces an event. Records are keyed by `orderId`, which keeps events for the same order in order on one partition.
- **Idempotent consumer**: the processed-event marker (`INSERT ... ON CONFLICT DO NOTHING`) and the side effect are written in one database transaction.
- **Retry and dead-letter topic**: `DefaultErrorHandler` with fixed back-off. Poison messages (`InvalidEventException`) skip the retries. Dead-lettered records carry exception headers and a dedicated DLT listener logs them.
- **Observability**: correlation IDs via `X-Correlation-Id` → MDC, ECS JSON structured logs in the `prod` profile, Actuator health, liveness and readiness probes, and a Prometheus endpoint.
- **OpenAPI 3** docs via springdoc (Swagger UI).
- **Containers and Kubernetes**: multi-stage, layered, non-root Dockerfiles; Docker Compose with Postgres and Kafka in KRaft mode; hardened K8s manifests.
- **CI**: GitHub Actions builds and tests on JDK 17 and 21, uploads the JaCoCo coverage report and builds the container images.

## Tech stack

| Area | Technology |
|------|------------|
| Language | Java 17 (bytecode target), builds on JDK 17/21 |
| Framework | Spring Boot 3.5, Spring Web MVC, Spring Data JPA, Spring for Apache Kafka |
| Messaging | Apache Kafka (KRaft) |
| Database | PostgreSQL 16 (runtime), H2 in PostgreSQL mode (tests), Flyway |
| API docs | springdoc-openapi 2 |
| Testing | JUnit 5, Mockito, AssertJ, MockMvc, `spring-kafka-test` EmbeddedKafka, Awaitility |
| Coverage | JaCoCo |
| Delivery | Docker, Docker Compose, Kubernetes (EKS-ready), GitHub Actions |

## Architecture

```mermaid
flowchart LR
    client([API client]) -->|POST /api/v1/orders| OS

    subgraph order-service
        OS[OrderController] --> SVC[OrderService<br/>@Transactional]
        SVC --> REPO[(orders DB<br/>PostgreSQL)]
        SVC -. in-process event .-> PUB[OrderEventPublisher<br/>AFTER_COMMIT]
    end

    PUB -->|key = orderId| T[[Kafka topic<br/>orders.order-created.v1]]

    subgraph notification-service
        L[OrderCreatedListener] --> H[OrderNotificationHandler<br/>@Transactional]
        H -->|ON CONFLICT DO NOTHING| PE[(processed_events)]
        H --> NS[NotificationSender]
        NS --> NL[(notification_log)]
        EH{{DefaultErrorHandler<br/>retry + back-off}}
        DL[DeadLetterListener]
    end

    T --> L
    L -. failure .-> EH
    EH -->|retries exhausted /<br/>InvalidEventException| DLT[[orders.order-created.v1.dlt]]
    DLT --> DL
```

**Module layout**

```
order-events-platform/
├── events-contract/        # Shared OrderCreatedEvent record + topic names
├── order-service/          # REST API, JPA, Flyway, Kafka producer
├── notification-service/   # Kafka consumer, idempotency ledger, retry/DLT
├── docker/postgres/        # DB init script (creates the notifications database)
├── k8s/                    # Namespace, ConfigMap, Deployments, Services, Kustomization
├── docker-compose.yml
└── .github/workflows/ci.yml
```

## Quick start

### Prerequisites

- JDK 17 or 21. Maven is not required, because the repo ships the Maven Wrapper (`./mvnw`).
- Docker with Compose v2, for the full stack.

### Option 1: everything in Docker Compose

```bash
cp .env.example .env          # then edit POSTGRES_PASSWORD
docker compose up --build
```

- Order API: http://localhost:8080 (Swagger UI: http://localhost:8080/swagger-ui.html)
- Notification service health: http://localhost:8081/actuator/health

### Option 2: run the services locally against Compose infrastructure

```bash
cp .env.example .env
docker compose up -d postgres kafka
./mvnw -B install -DskipTests
set -a && source .env && set +a

./mvnw -pl order-service spring-boot:run
# in another terminal (the notification DB is created by docker/postgres/01-create-databases.sql)
./mvnw -pl notification-service spring-boot:run
```

## Configuration

All settings can be overridden with environment variables.

| Variable | Service | Default | Description |
|----------|---------|---------|-------------|
| `DB_URL` | both | `jdbc:postgresql://localhost:5432/orders` / `.../notifications` | JDBC URL |
| `DB_USERNAME` | both | `orders` / `notifications` | DB user |
| `DB_PASSWORD` | both | *(none)* | DB password (from `.env` or a K8s Secret) |
| `DB_POOL_SIZE` | both | `10` / `5` | Hikari max pool size |
| `KAFKA_BOOTSTRAP_SERVERS` | both | `localhost:9092` | Kafka brokers |
| `ORDER_CREATED_TOPIC` | both | `orders.order-created.v1` | Main topic |
| `ORDER_CREATED_DLT_TOPIC` | notification | `orders.order-created.v1.dlt` | Dead-letter topic |
| `ORDER_CREATED_PARTITIONS` / `ORDER_CREATED_REPLICAS` | both | `3` / `1` | Topic sizing when auto-provisioned |
| `RETRY_INTERVAL_MS` | notification | `1000` | Fixed back-off between attempts |
| `RETRY_MAX_RETRIES` | notification | `3` | Retries before dead-lettering |
| `LISTENER_CONCURRENCY` | notification | `3` | Consumer threads (≤ partitions) |
| `CONSUMER_GROUP_ID` | notification | `notification-service` | Kafka consumer group |
| `SERVER_PORT` | both | `8080` / `8081` | HTTP port |
| `SPRING_PROFILES_ACTIVE` | both | *(none)* | `prod` enables JSON logs and disables Swagger UI |
| `SWAGGER_UI_ENABLED` | order | `false` in `prod` | Toggle Swagger UI in prod profile |

## API examples

```bash
# Place an order
curl -i -X POST http://localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -H 'X-Correlation-Id: demo-123' \
  -d '{
        "customerId": "CUST-1001",
        "currency": "USD",
        "items": [
          {"sku": "SKU-KB-001", "quantity": 1, "unitPrice": 59.99},
          {"sku": "SKU-MS-002", "quantity": 2, "unitPrice": 24.99}
        ]
      }'
# -> 201 Created, Location: /api/v1/orders/{id}

# Fetch an order (seeded sample)
curl http://localhost:8080/api/v1/orders/0b6f1d2e-1111-4c1a-9a01-000000000001

# List a customer's orders (paged, newest first)
curl 'http://localhost:8080/api/v1/orders?customerId=CUST-1001&page=0&size=20'

# Validation error -> RFC 7807 problem detail
curl -s -X POST http://localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' -d '{"customerId":"","currency":"usd","items":[]}'
```

Example error response:

```json
{
  "type": "https://errors.orders.example/validation",
  "title": "Validation error",
  "status": 400,
  "detail": "Request validation failed",
  "instance": "/api/v1/orders",
  "errors": [{"field": "currency", "message": "must be a 3-letter ISO-4217 code"}]
}
```

Inspect events with the Kafka CLI inside the broker container:

```bash
docker compose exec kafka /opt/kafka/bin/kafka-console-consumer.sh \
  --bootstrap-server localhost:9092 --topic orders.order-created.v1 --from-beginning
```

## Testing

```bash
./mvnw -B verify            # unit + integration tests, JaCoCo reports
open order-service/target/site/jacoco/index.html
```

The tests need no external infrastructure, no Docker daemon, no Postgres and no Kafka install:

| Module | Test | What it covers |
|--------|------|----------------|
| order-service | `OrderTest` | Aggregate invariants and total calculation |
| order-service | `OrderServiceTest` | Service logic and event emission (Mockito) |
| order-service | `OrderRepositoryTest` | Flyway migrations and JPA mapping on H2 (PostgreSQL mode) |
| order-service | `OrderApiIntegrationTest` | MockMvc and EmbeddedKafka: 201/200/400/404 paths, ProblemDetail shape, the event really reaches Kafka |
| notification-service | `OrderNotificationHandlerTest` | Idempotency branch logic |
| notification-service | `OrderCreatedListenerIntegrationTest` | EmbeddedKafka: happy path, duplicate delivery processed once, poison message sent to DLT, transient failure retried then sent to DLT with rollback |

## Deploying to Kubernetes / Amazon EKS

```bash
# 1. Build and push images (example: Amazon ECR)
docker build -f order-service/Dockerfile -t <acct>.dkr.ecr.<region>.amazonaws.com/order-service:<sha> .
docker build -f notification-service/Dockerfile -t <acct>.dkr.ecr.<region>.amazonaws.com/notification-service:<sha> .

# 2. Provide DB credentials as a Secret (see k8s/secret.example.yaml), then apply
kubectl apply -k k8s/
```

EKS notes:

- **Images**: push to ECR and update the `image:` fields, or use a Kustomize `images:` override in CI. Tag images with the git SHA, not `latest`.
- **Kafka**: Amazon MSK works as a drop-in. Point `KAFKA_BOOTSTRAP_SERVERS` at the TLS or IAM listener and add the `aws-msk-iam-auth` client settings if you use IAM auth. Set replicas to 3 and `min.insync.replicas` to 2.
- **Database**: Amazon RDS / Aurora PostgreSQL. Inject credentials with the External Secrets Operator or the Secrets Store CSI driver backed by AWS Secrets Manager, never with a committed Secret.
- **Identity**: use IRSA or EKS Pod Identity for any AWS API access. The manifests disable service-account token automounting by default.
- **Ingress**: expose `order-service` through the AWS Load Balancer Controller (ALB Ingress) with TLS from ACM. `notification-service` stays internal.
- **Scaling**: add an HPA on CPU for `order-service`. For `notification-service`, scale on consumer lag (KEDA Kafka scaler) and keep replicas × concurrency ≤ partitions.
- **Probes**: `startupProbe`, `livenessProbe` and `readinessProbe` use Spring Boot's `/actuator/health/{liveness,readiness}` groups. `preStop` plus graceful shutdown drain in-flight requests during rollouts. (`preStop.sleep` needs Kubernetes 1.30 or later. On older clusters, use an `exec` sleep instead.)

## Security considerations

- **No secrets in the repo**: credentials come from environment variables, `.env` (gitignored) or Kubernetes Secrets. `.env.example` and `secret.example.yaml` contain placeholders only.
- **Hardened containers**: images run as a non-root UID with a read-only root filesystem, all Linux capabilities dropped, `seccompProfile: RuntimeDefault` and no privilege escalation.
- **Input validation** on every request DTO. Unexpected errors return a generic 500 problem detail, so stack traces are never exposed.
- **Correlation ID** headers are checked against a strict pattern to prevent log injection.
- **No PII in events**: events carry only an opaque `customerId`, never names, emails or addresses.
- **Swagger UI** is disabled by default in the `prod` profile.
- **Not production-ready yet**: the API is unauthenticated in this demo. Production should put it behind an OAuth2 resource server (see the companion `secure-api-rbac` project) or an API gateway, and enable TLS/SASL on Kafka.

## Limitations and future enhancements

- **Outbox pattern**: publishing after commit can lose an event if the process crashes between the commit and the send. A transactional outbox (with a poller or Debezium CDC) would give at-least-once delivery from the database.
- **Schema registry**: move to Avro or Protobuf with Confluent or Apicurio Schema Registry and compatibility checks in CI.
- **Order lifecycle**: add cancel and confirm transitions, emit `OrderCancelled` events, and add saga-based payment and inventory steps.
- **DLT replay tooling**: add an admin endpoint or CLI to re-drive dead-lettered messages after a fix.
- **Testcontainers**: run integration tests against real PostgreSQL and Kafka images where a Docker daemon is available.
- **Observability**: add OpenTelemetry tracing across HTTP and Kafka (Micrometer Tracing) and Grafana dashboards.
- **API security**: authentication, authorization and rate limiting at the edge.
- **Packaging**: replace raw K8s manifests with a Helm chart, and add an HPA, PodDisruptionBudget and NetworkPolicies.

---

Representative portfolio project built independently with synthetic data; not derived from any employer or client codebase.

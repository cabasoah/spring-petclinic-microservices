# Experiment 3 — EDA Micro-Refactor Configuration

## JMeter Test Plan
- **File**: `exp3_eda_refactor.jmx`
- **Thread counts tested**: 100, 500, 1000 users (modify `num_threads` before each run)
- **Ramp-up**: 30 seconds
- **Duration**: 120 seconds (scheduler enabled)
- **Loops**: 10000
- **Content-Type**: `application/json` header added
- **Endpoints hit**:
  - `GET /api/vet/vets` — Veterinarians page
  - `GET /api/gateway/owners/1` — Single owner lookup
  - `POST /api/visit/owners/1/pets/1/visits` — Create visit (**async via RabbitMQ**)
  - `GET /api/gateway/owners/1` — Owner lookup again
- **Visit POST body**: Empty (controller uses `defaultVisit()` which generates `"JMeter EDA visit"`)

## Architecture Change: Synchronous → Event-Driven
```
BEFORE (Exp 1):  Client → API Gateway → visits-service (sync JPA save)
AFTER  (Exp 3):  Client → API Gateway → AsyncVisitCommandController
                                             ↓ (publishes to RabbitMQ)
                                        RabbitMQ exchange: "petclinic.events"
                                             ↓ (consumes)
                                        VisitEventListener → JPA save
```

## Key Configuration Changes (vs Baseline)

### API Gateway Route (`application.yml`)
```yaml
- id: visits-service
  uri: lb://visits-service
  predicates:
    - Path=/api/visit/**
    - Method=GET          # ← Only GET goes to visits-service
  filters:
    - StripPrefix=2
    - CircuitBreaker=name=visits,fallbackUri=forward:/fallback
```
POST requests to `/api/visit/**` are NOT matched by this route, so they fall through to `AsyncVisitCommandController` (which is a `@RestController` in the api-gateway itself).

### RabbitMQ Config (added to api-gateway and visits-service `application.yml`)
```yaml
spring:
  rabbitmq:
    host: ${RABBITMQ_HOST:localhost}
    port: ${RABBITMQ_PORT:5672}
    username: ${RABBITMQ_USERNAME:guest}
    password: ${RABBITMQ_PASSWORD:guest}
```

### Docker Compose Additions
- `rabbitmq` service: `rabbitmq:3-management` on ports 5672 (AMQP) and 15672 (management UI)
- `visits-service` and `api-gateway`: RabbitMQ environment variables added, `depends_on: rabbitmq`

### Database Schema Changes
```sql
-- Added columns to visits table:
status      VARCHAR(32) DEFAULT 'ACTIVE'
created_at  TIMESTAMP DEFAULT CURRENT_TIMESTAMP
```

### New Dependencies (`pom.xml`)
- `spring-boot-starter-amqp` added to `api-gateway` and `visits-service`

## Service Ports
| Service | Port |
|---------|------|
| All baseline services | Same as Exp 1 |
| RabbitMQ (AMQP) | 5672 |
| RabbitMQ (Management UI) | 15672 |

## Results Summary
| Users | Baseline p99 (ms) | EDA p99 (ms) | Improvement | EDA Throughput (req/s) |
|-------|--------------------|--------------|-------------|------------------------|
| 100 | 375 | 99 | 73.60% | 1754.31 |
| 500 | 691 | 574 | 16.93% | 2418.96 |
| 1000 | 1875 | 1113.99 | 40.59% | 2263.36 |

## Files in This Folder
- `exp3_eda_refactor.jmx` — JMeter test plan
- `Exp3_EDA_vs_Baseline.csv` — EDA vs Baseline comparison
- `Exp3_JMeter_100.jtl`, `Exp3_JMeter_500.jtl`, `Exp3_JMeter_1000.jtl` — Raw JMeter results
- `Exp3_Grafana_1000_users.png` — Grafana dashboard at 1000 users
- `Exp3_RabbitMQ_Dashboard.png` — RabbitMQ management UI
- `Exp3_RabbitMQ_visit_confirmed.png` — RabbitMQ visit confirmed queue

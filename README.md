# Spring PetClinic Microservices — CS 6075 Experiment Suite

> Forked from [spring-petclinic/spring-petclinic-microservices](https://github.com/spring-petclinic/spring-petclinic-microservices).  
> This branch (`FINAL`) unifies four experiments on microservice architecture patterns.

## Prerequisites

- Java 17+
- Docker & Docker Compose
- Apache JMeter 5.6+
- Maven 3.9+ (or use included `./mvnw`)

## Quick Start

```bash
# Build all services
./mvnw clean install -DskipTests

# Start everything
docker-compose up -d --build

# Verify services are healthy
docker-compose ps
```

### Service Ports

| Service | Port | URL |
|---------|------|-----|
| API Gateway | 8080 | http://localhost:8080 |
| Customers Service | 8081 | http://localhost:8081 |
| Visits Service | 8082 | http://localhost:8082 |
| Vets Service | 8083 | http://localhost:8083 |
| Config Server | 8888 | http://localhost:8888 |
| Discovery (Eureka) | 8761 | http://localhost:8761 |
| RabbitMQ Management | 15672 | http://localhost:15672 (guest/guest) |
| Grafana | 3030 | http://localhost:3030 |
| Prometheus | 9091 | http://localhost:9091 |
| Zipkin | 9411 | http://localhost:9411 |
| Admin Server | 9090 | http://localhost:9090 |

---

## Experiments Overview

| # | Experiment | Pattern |
|---|-----------|--------|
| 1 | Baseline Scalability | Load testing (100/500/1000 users) |
| 2 | Chaos Resilience | Circuit Breaker + Chaos Monkey |
| 3 | EDA Micro-Refactor | Event-Driven Architecture via RabbitMQ |
| 4 | Saga Data Consistency | Saga pattern with compensation |

Each experiment has its own JMeter test plan in the `jmeter/` directory.

---

## Experiment 1 — Baseline Scalability

**Goal**: Measure baseline latency and throughput under increasing load.

**What changed**: Added healthcheck and restart policy to visits-service in docker-compose.

```bash
# Run with 100 users (edit num_threads in JMX for 500/1000)
jmeter -n -t jmeter/exp1_exp2_baseline_chaos.jmx \
  -l results/experiment-1/run.jtl
```

**Results**: `FINAL_RESULTS/Exp1/` — summary CSVs and screenshots. Raw JTL files in `results/experiment-1/`.

---

## Experiment 2 — Chaos Resilience

**Goal**: Validate circuit breaker behavior when visits-service is killed mid-test.

**What changed**: 
- Resilience4j circuit breaker configured on `visits` route in api-gateway
- Chaos Monkey enabled on visits-service via REST API

```bash
# 1. Start services
docker-compose up -d --build

# 2. Enable Chaos Monkey watcher on visits-service
curl -X POST http://localhost:8082/actuator/chaosmonkey/watchers \
  -H "Content-Type: application/json" \
  -d @scripts/chaos/watcher_enable_restcontroller.json

# 3. Enable kill-application attack
curl -X POST http://localhost:8082/actuator/chaosmonkey/assaults \
  -H "Content-Type: application/json" \
  -d @scripts/chaos/attacks_enable_killapplication.json

# 4. Run JMeter
jmeter -n -t jmeter/exp1_exp2_baseline_chaos.jmx \
  -l results/experiment-2/run.jtl
```

**Key config** (`api-gateway application.yml`):
```yaml
resilience4j.circuitbreaker.instances.visits:
  slidingWindowSize: 10
  failureRateThreshold: 50
  waitDurationInOpenState: 10s
```

**Results**: `FINAL_RESULTS/Exp2/` — chaos summary CSV, circuit breaker state log, screenshots. Raw JTL in `results/experiment-2/`.

---

## Experiment 3 — EDA Micro-Refactor

**Goal**: Replace synchronous visit creation with async event-driven flow via RabbitMQ. Compare latency/throughput against baseline.

**What changed**:
- `AsyncVisitCommandController` in api-gateway publishes `VisitCreatedEvent` to RabbitMQ
- `VisitEventListener` in visits-service consumes and persists visits
- Gateway route filter `- Method=GET` ensures only GETs route to visits-service; POSTs go to the async controller
- RabbitMQ added to docker-compose, api-gateway, and visits-service configs
- `status` and `created_at` columns added to visits table

```bash
# Run with 100 users (edit num_threads in JMX for 500/1000)
jmeter -n -t jmeter/exp3_eda_refactor.jmx \
  -l results/experiment-3/run.jtl
```

**Results**: `FINAL_RESULTS/Exp3/Exp3_EDA_vs_Baseline.csv`

| Users | Baseline p99 | EDA p99 | Improvement |
|-------|-------------|---------|-------------|
| 100 | 375 ms | 99 ms | 73.6% |
| 500 | 691 ms | 574 ms | 16.9% |
| 1000 | 1875 ms | 1114 ms | 40.6% |

---

## Experiment 4 — Saga Data Consistency

**Goal**: Validate saga pattern with compensation. A `mock-billing-service` randomly fails billing, triggering visit cancellation.

**What changed** (on top of Exp 3):
- `mock-billing-service` microservice added (listens on `visit.confirmed` queue, randomly fails)
- `VisitCompensationListener` marks visits as `CANCELLED` on `VisitFailedEvent`
- `VisitIntegrityResource` exposes `GET /visits/integrity?minutes=N` for validation
- `createdAt` field added to Visit model for time-window queries

```bash
# Run JMeter (100 users, 1 iteration)
jmeter -n -t jmeter/exp4_saga_validation.jmx \
  -l results/experiment-4/run.jtl

# Validate data integrity
curl http://localhost:8082/visits/integrity?minutes=10
```

**Results**: `FINAL_RESULTS/Exp4/Exp4_Saga_Data_Integrity.txt`
```
ACTIVE=49, CANCELLED=56, TOTAL=105
→ Saga compensation confirmed working
```

---

## Repository Structure

```
jmeter/
  exp1_exp2_baseline_chaos.jmx    # Exp 1 & 2 JMeter plan
  exp3_eda_refactor.jmx           # Exp 3 JMeter plan
  exp4_saga_validation.jmx        # Exp 4 JMeter plan
  petclinic_full_scenario.jmx     # Default (Exp 4 version)

results/
  experiment-1/                   # Exp 1 raw JTL files
  experiment-2/                   # Exp 2 raw JTL files
  experiment-3/                   # Exp 3 raw JTL files
  experiment-4/                   # Exp 4 raw JTL files

FINAL_RESULTS/
  Exp1/                           # Exp 1 summary CSVs, Grafana screenshots, JMX
  Exp2/                           # Exp 2 chaos CSV, circuit breaker log, screenshots, JMX
  Exp3/                           # Exp 3 EDA comparison CSV, RabbitMQ screenshots, JMX
  Exp4/                           # Exp 4 saga integrity report, screenshots, JMX

screenshots/                      # Grafana, RabbitMQ, JMeter screenshots
scripts/chaos/                    # Chaos Monkey attack/watcher configs
mock-billing-service/             # Exp 4 saga billing mock service
spring-petclinic-*/               # Microservice modules
docker-compose.yml                # Full stack compose (all experiments)
```

> All experiment work lives on the `FINAL` branch.

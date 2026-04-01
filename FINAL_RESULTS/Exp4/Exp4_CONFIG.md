# Experiment 4 — Saga Data Consistency Configuration

## JMeter Test Plan
- **File**: `exp4_saga_validation.jmx`
- **Thread count**: 100 users
- **Ramp-up**: 30 seconds
- **Duration**: Single iteration (scheduler disabled, loops=1)
- **Content-Type**: `application/json` header
- **Visit POST body**: `{"date":"2026-03-28","description":"JMeter saga visit"}`
- **Endpoints hit**: Same as Exp 3 but with explicit JSON body on the POST

## Architecture: Saga Pattern with Compensation
```
Client → API Gateway → AsyncVisitCommandController
                             ↓ (publishes VisitCreatedEvent)
                        RabbitMQ exchange: "petclinic.events"
                        ↙                              ↘
          VisitEventListener                   BillingEventListener
          (visits-service)                     (mock-billing-service)
          - saves visit (ACTIVE)               - simulates billing
          - publishes VisitConfirmedEvent       - randomly fails → publishes
                                                 VisitFailedEvent
                                                      ↓
                                          VisitCompensationListener
                                          (visits-service)
                                          - marks visit as CANCELLED
```

## Key Configuration Changes (vs Exp 3)

### New Microservice: `mock-billing-service`
- Standalone Spring Boot app in `mock-billing-service/`
- Listens on `visit.confirmed` queue
- Simulates billing failures (randomly publishes `VisitFailedEvent`)
- Triggers compensation in visits-service

### Docker Compose Addition
```yaml
mock-billing-service:
  build: ./mock-billing-service
  container_name: mock-billing-service
  environment:
    - RABBITMQ_HOST=rabbitmq
    - RABBITMQ_PORT=5672
    - RABBITMQ_USERNAME=guest
    - RABBITMQ_PASSWORD=guest
  depends_on:
    rabbitmq:
      condition: service_healthy
```

### Root `pom.xml`
```xml
<module>mock-billing-service</module>  <!-- added -->
```

### Visit Model Enhancement
```java
// Added to Visit.java:
@Column(name = "created_at", insertable = false, updatable = false)
@Temporal(TemporalType.TIMESTAMP)
private Date createdAt;
```

### Data Integrity Validation Endpoint
```
GET /visits/integrity?minutes=10
```
Returns count of visits by status (ACTIVE vs CANCELLED) within the time window.

### VisitEventListener Enhancement (null-safe)
```java
// Supports both "visitDate" and "date" keys in the event map
// Null-checks on all event fields before processing
```

## Service Ports
| Service | Port |
|---------|------|
| All baseline + EDA services | Same as Exp 3 |
| mock-billing-service | Internal only (no exposed port) |

## Validation Results
```
Counts by status:
  ACTIVE    = 49
  CANCELLED = 56
  TOTAL     = 105

Conclusion: Saga compensation is working — both ACTIVE visits
and compensated (CANCELLED) visits exist in the database.
```

## Files in This Folder
- `exp4_saga_validation.jmx` — JMeter test plan
- `Exp4_JMeter_100.jtl` — Raw JMeter results
- `Exp4_Saga_Data_Integrity.txt` — Data integrity validation output
- `Exp4_Db_Query.png` — Database query showing visit statuses
- `Exp4_RabbitMQ.png` — RabbitMQ exchanges and queues
- `Exp4_RabbitMQ_VisitFailedQueue.png` — Failed visit queue in RabbitMQ

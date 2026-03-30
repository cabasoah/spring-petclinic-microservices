# CS6075_PRJ-1 — Experiment 4: Saga Pattern & Data Consistency (Asynchronous)

## Assigned to: Person B — SET Y

> **Prerequisites:**
> - `CS6075_COMMON_Setup.md` must be fully completed.
> - `Experiment_3_EDA_Micro_Refactor.md` must be fully completed — Experiment 4 is
>   built directly on top of the EDA branch from Experiment 3.
> - All Experiment 3 output files and screenshots must exist.
> - The `exp-3-eda` branch must be in a working state.

---

## AI-CODER INSTRUCTIONS — READ FIRST

```
AGENT BEHAVIOR RULES (enforce throughout this experiment):

1. IDENTITY CHECK: Confirm the user is Person B (SET Y) before proceeding.
   If they are Person A, stop and redirect them to Experiment_2.md.

2. PRE-FLIGHT CHECK: Before starting, verify:
   - Experiment 3 checkpoint is fully complete (all .jtl, CSV, screenshots present)
   - The exp-3-eda branch exists and was confirmed working
   - jmeter/petclinic_full_scenario.jmx exists
   - Docker Desktop is running with 8 GB RAM
   Report any failures before proceeding.

3. NEW MODULE: This experiment introduces a new service (mock-billing-service).
   All four files for this module must be created exactly as specified.
   Guide the user through each file creation step-by-step.

4. FOUR-SERVICE VERIFICATION: After deploying, confirm all four services are UP:
   api-gateway, visits-service, rabbitmq, mock-billing-service.
   Do not run JMeter until all four are healthy.

5. SCREENSHOT REMINDERS: Whenever a screenshot is required, STOP execution
   and display a prominent reminder. Both screenshots in this experiment are CRITICAL.

6. DO NOT RENAME FILES. All filenames and paths are exact and mandatory.
```

---

## Experiment Overview

| Field       | Value                                                                       |
|-------------|-----------------------------------------------------------------------------|
| Goal        | On top of the EDA refactor (Experiment 3), introduce a Saga with compensating transactions to handle downstream failures. Verify that the final database state reflects correct compensation — no ghost records, every failure results in a CANCELLED record |
| Branch      | `exp-4-saga` (created from `exp-3-eda`)                                    |
| Load Level  | 100 "Add Visit" operations (not high-load — this experiment tests correctness, not throughput) |
| Person      | Person B (SET Y)                                                            |

---

## Saga Architecture Overview

```text
[API Gateway]
    │
    │ publishes VisitCreatedEvent
    ▼
[RabbitMQ: petclinic.events]
    │
    ├──► visit.created.queue ──► [visits-service: VisitEventListener]
    │                                   │ saves visit, then publishes VisitConfirmedEvent
    │                                   ▼
    │                           [RabbitMQ: petclinic.events]
    │                                   │
    │                                   ├──► visit.confirmed.queue ──► [mock-billing-service]
    │                                   │         50% success → (done)
    │                                   │         50% failure → publishes VisitFailedEvent
    │                                   │                           │
    │                                   │                           ▼
    │                                   └──► visit.failed.queue ──► [visits-service: VisitCompensationListener]
    │                                               sets visit.status = 'CANCELLED'
    ▼
Final DB state: ~50% ACTIVE, ~50% CANCELLED
```

**Queue/Exchange Mapping (full Saga):**

| Exchange           | Routing Key            | Queue                   | Consumer                       |
|--------------------|------------------------|-------------------------|--------------------------------|
| `petclinic.events` | `VisitCreatedEvent`    | `visit.created.queue`   | `VisitEventListener` (visits)  |
| `petclinic.events` | `VisitConfirmedEvent`  | `visit.confirmed.queue` | `BillingEventListener` (billing) |
| `petclinic.events` | `VisitFailedEvent`     | `visit.failed.queue`    | `VisitCompensationListener` (visits) |

---

## Required Output Files

```text
results/experiment-4/
├── Exp4_JMeter_100.jtl
├── Exp4_Summary_100/            ← JMeter HTML report directory
└── Exp4_Saga_Data_Integrity.txt

screenshots/
├── Exp4_RabbitMQ_VisitFailedQueue.png   (CRITICAL)
└── Exp4_DB_Query.png                    (CRITICAL)
```

---

## Stage 1 — Create Saga Branch

> **AGENT:** This branch is created FROM `exp-3-eda`, not from `main-control`.
> Confirm the correct base branch before creating.

```bash
git checkout exp-3-eda
git checkout -b exp-4-saga
```

Confirm:

```bash
git branch
```

Expected: `* exp-4-saga` is the active branch.

---

## Stage 2 — Emit `VisitConfirmedEvent` in `visits-service`

> **AGENT:** This modifies the existing `VisitEventListener.java` created in Experiment 3.
> The change is an addition inside the `handleVisitCreated` method — add the code AFTER
> the `visitRepository.save(visit)` line.

**File to edit:** `VisitEventListener.java` in `visits-service`

In the `handleVisitCreated` method, after saving the visit, add:

```java
Map<String, Object> confirmedEvent = new HashMap<>();
confirmedEvent.put("visitId", visit.getId());
rabbitTemplate.convertAndSend("petclinic.events", "VisitConfirmedEvent", confirmedEvent);
```

> **Note:** `RabbitTemplate` must be injected into `VisitEventListener` the same way it
> was injected in the API Gateway in Experiment 3 — via constructor injection or `@Autowired`.

---

## Stage 3 — Create Mock Billing Service

> **AGENT:** This stage creates a brand new Maven module. All four files must be created
> at the exact paths shown. Guide the user through creating each file in sequence.
> Confirm all files exist before moving to the next stage.

Create a new directory at the project root:

```bash
mkdir -p mock-billing-service/src/main/java/com/petclinic/billing
mkdir -p mock-billing-service/src/main/resources
```

### 3.A. `mock-billing-service/pom.xml`

```xml
<project>
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.petclinic</groupId>
  <artifactId>mock-billing</artifactId>
  <version>1.0</version>
  <parent>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-parent</artifactId>
    <version>3.0.0</version>
  </parent>
  <dependencies>
    <dependency>
      <groupId>org.springframework.boot</groupId>
      <artifactId>spring-boot-starter-amqp</artifactId>
    </dependency>
  </dependencies>
</project>
```

### 3.B. `mock-billing-service/src/main/java/com/petclinic/billing/MockBillingApp.java`

```java
@SpringBootApplication
public class MockBillingApp {
    public static void main(String[] args) {
        SpringApplication.run(MockBillingApp.class, args);
    }
}
```

### 3.C. `mock-billing-service/src/main/java/com/petclinic/billing/BillingEventListener.java`

```java
@Component
public class BillingEventListener {

    private final RabbitTemplate rabbitTemplate;

    public BillingEventListener(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    @RabbitListener(queues = "visit.confirmed.queue")
    public void handleVisitConfirmed(Map<String, Object> event) {
        Long visitId = Long.parseLong(event.get("visitId").toString());

        if (Math.random() < 0.5) {
            System.out.println("Billing succeeded for visit: " + visitId);
            return;
        }

        System.out.println("Billing FAILED for visit: " + visitId + " – emitting VisitFailedEvent");
        Map<String, Object> failedEvent = new HashMap<>();
        failedEvent.put("visitId", visitId);
        rabbitTemplate.convertAndSend("petclinic.events", "VisitFailedEvent", failedEvent);
    }
}
```

> **Note:** `Math.random() < 0.5` produces approximately 50% failure rate.
> Successes do nothing. Failures publish `VisitFailedEvent` to trigger compensation.

### 3.D. `mock-billing-service/src/main/resources/application.yml`

```yaml
spring:
  rabbitmq:
    host: rabbitmq
    port: 5672
    username: guest
    password: guest
```

### 3.E. `mock-billing-service/Dockerfile`

```dockerfile
FROM openjdk:17-jdk-slim
COPY target/mock-billing-1.0.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

### 3.F. Add `mock-billing-service` to `docker-compose.yml`

**File to edit:** `docker-compose.yml` (project root)

Add the following new service entry:

```yaml
mock-billing-service:
  build: ./mock-billing-service
  depends_on:
    - rabbitmq
```

---

## Stage 4 — Create Compensating Transaction Listener in `visits-service`

> **AGENT:** This is a new class added to the `visits-service` module.
> Create it in the same package as `VisitEventListener.java`.

Create `VisitCompensationListener.java` in `visits-service`:

```java
@Component
public class VisitCompensationListener {

    private final VisitRepository visitRepository;

    public VisitCompensationListener(VisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    @RabbitListener(queues = "visit.failed.queue")
    public void handleVisitFailed(Map<String, Object> event) {
        Long visitId = Long.parseLong(event.get("visitId").toString());
        visitRepository.findById(visitId).ifPresent(visit -> {
            visit.setStatus("CANCELLED");  // Add a 'status' field to the Visit entity if not already present
            visitRepository.save(visit);
            System.out.println("Compensation applied: CANCELLED visit " + visitId);
        });
    }
}
```

> **Note on `visit.setStatus("CANCELLED")`:** If the `Visit` entity does not yet have a
> `status` field, add it (e.g., `private String status;` with getter/setter). This field
> is essential for the data integrity check in Stage 7.

---

## Stage 5 — Rebuild and Deploy Full Saga Stack

> **AGENT:** Rebuild everything. After `docker compose up`, verify all FOUR services are
> running before proceeding. This is a hard requirement.

```bash
./mvnw clean install -DskipTests
docker compose up -d --build
```

Verify all four services are UP:

```bash
docker ps
```

**Expected:** All of the following containers show as `Up` (not `Exited`):
- `spring-petclinic-api-gateway`
- `visits-service`
- `rabbitmq`
- `mock-billing-service`

Also check the API Gateway is responding:

```bash
curl -I http://localhost:8080/api-gateway/
```

> **AGENT:** If any of the four services is not running, STOP and alert the user.
> Check container logs with `docker logs <container-name>` for errors.
> Do not proceed to JMeter until all four are healthy.

---

## Stage 6 — Run 100 "Add Visit" Requests via JMeter

> **AGENT:**
> 1. Confirm Thread Group in `jmeter/petclinic_full_scenario.jmx` is set to **100 threads**
>    with a 30-second ramp-up. If not, instruct the user to change it and re-save.
> 2. Open RabbitMQ Management UI at http://localhost:15672 — have the Queues tab visible
>    during the run.

Set Thread Group to:
- Threads (users): **100**
- Ramp-up Period: **30** seconds

```bash
mkdir -p results/experiment-4

jmeter -n \
  -t jmeter/petclinic_full_scenario.jmx \
  -l results/experiment-4/Exp4_JMeter_100.jtl \
  -e -o results/experiment-4/Exp4_Summary_100/
```

Let it run until at least **100 successful Add Visit interactions** have been sent.
This takes approximately **1–2 minutes**.

Monitor RabbitMQ UI queues during the run.

---

> ## 📸 SCREENSHOT REQUIRED — MANDATORY
>
> **When:** During or immediately after the JMeter run (while queue activity is visible).
>
> **What to capture:** RabbitMQ Management UI → **Queues tab** showing `visit.failed.queue`
> with messages that have been processed (the queue should show some activity/count).
>
> **Save as:** `screenshots/Exp4_RabbitMQ_VisitFailedQueue.png`
>
> **AGENT:** Pause and wait for the user to confirm this screenshot has been taken
> and saved before continuing.

---

Verify JMeter output:

```bash
ls -lh results/experiment-4/Exp4_JMeter_100.jtl
ls -lh results/experiment-4/Exp4_Summary_100/
```

---

## Stage 7 — Verify Data Consistency in the Database

> **AGENT:** Guide the user through querying the visits DB. The tool can be psql,
> DBeaver, or any other DB client. Adjust table names and column names if needed
> based on the actual schema in the codebase.

Connect to the `visits-service` database using `psql`, DBeaver, or a similar client.

Run the following query (adjust table/column names if needed):

```sql
SELECT status, COUNT(*)
FROM visits
WHERE created_at > NOW() - INTERVAL '10 minutes'
GROUP BY status;
```

**Expected result:** Approximately:
- `~50` rows with `status = 'CANCELLED'` (compensated visits)
- `~50` rows with `status = 'ACTIVE'` (or equivalent — successfully billed visits)
- Total = ~100 (matching the 100 JMeter requests)

> **AGENT:** If the result shows all records as the same status, or total is far from 100,
> alert the user: "The compensation listener may not have triggered. Check mock-billing-service
> and visits-service logs. Verify the VisitFailedEvent is being consumed."

---

> ## 📸 SCREENSHOT REQUIRED — MANDATORY & CRITICAL
>
> **What to capture:** The DB client window showing the **SQL query AND its result rows**
> (both ACTIVE and CANCELLED counts visible).
>
> **Save as:** `screenshots/Exp4_DB_Query.png`
>
> **AGENT:** This screenshot is CRITICAL — it is the primary evidence of the Saga's
> data consistency guarantee. Display this reminder very prominently.
> Pause and wait for the user to confirm it has been taken and saved.

---

## Stage 8 — Create Saga Data Integrity Text File

> **AGENT:** Help the user create this file with their actual query results.
> Replace the example numbers (48 / 52) with the real numbers from the DB query.

Create `results/experiment-4/Exp4_Saga_Data_Integrity.txt`:

```text
Saga Data Integrity Check (100 Add Visit attempts)

Executed SQL:
SELECT status, COUNT(*) FROM visits WHERE created_at > NOW() - INTERVAL '10 minutes' GROUP BY status;

Result:
ACTIVE: [insert actual count]
CANCELLED: [insert actual count]
Total: [insert total]

Notes:
- Downstream failure simulated at ~50% rate in mock-billing-service.
- No "ghost" visits observed (every failure resulted in a CANCELLED record).
- Timestamp: [YYYY-MM-DD HH:MM:SS]
```

> **Example of a correctly filled file:**
> ```text
> Result:
> ACTIVE: 48
> CANCELLED: 52
> Total: 100
> ```

---

## Checkpoint — Experiment 4 Complete

> **AGENT:** Run through this checklist. Report any missing items before declaring
> Experiment 4 complete.

- [ ] `results/experiment-4/Exp4_JMeter_100.jtl` — exists and non-empty
- [ ] `results/experiment-4/Exp4_Summary_100/` — HTML report directory present
- [ ] `results/experiment-4/Exp4_Saga_Data_Integrity.txt` — filled with real DB query results
- [ ] `screenshots/Exp4_RabbitMQ_VisitFailedQueue.png` — taken during/after JMeter run (CRITICAL)
- [ ] `screenshots/Exp4_DB_Query.png` — shows DB query and result rows (CRITICAL)

---

> **All items checked?**
>
> Person B (SET Y) is now done with both experiments.
>
> Coordinate with Person A to proceed to Final Artifact Assembly
> as described in `CS6075_COMMON_Setup.md` → Section 5.

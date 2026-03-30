# CS6075_PRJ-1 — Experiment 3: EDA Micro-Refactor (Asynchronous)

## Assigned to: Person B — SET Y

> **Prerequisites:** You must have completed ALL steps in `CS6075_COMMON_Setup.md` before
> starting this file — including tool verification, repo clone, baseline build, and JMeter
> test plan creation. Do not begin here without confirming those are done.

---

## AI-CODER INSTRUCTIONS — READ FIRST

```
AGENT BEHAVIOR RULES (enforce throughout this experiment):

1. IDENTITY CHECK: Confirm the user is Person B (SET Y) before proceeding.
   If they are Person A, stop and redirect them to Experiment_1.md.

2. PRE-FLIGHT CHECK: Before starting, verify:
   - jmeter/petclinic_full_scenario.jmx exists
   - Docker Desktop is running with 8 GB RAM allocated
   - The baseline stack is currently DOWN (clean state before this experiment)
   - You are on branch main-control (git branch should show * main-control)
   Report any failures before proceeding.

3. STAGE-BY-STAGE EXECUTION: Complete each section fully before moving on.
   Validate each code change compiles and the service behaves as expected
   before running JMeter.

4. THREE JMETER RUNS: This experiment has three load runs (100 / 500 / 1000 users).
   Update the Thread Group thread count and re-save the JMX before each run.
   Confirm each output file exists before starting the next run.

5. SCREENSHOT REMINDERS: Whenever a screenshot is required, STOP execution
   and display a prominent reminder to the user. Wait for acknowledgment
   before continuing.

6. DO NOT RENAME FILES. All filenames and paths are exact and mandatory.
```

---

## Experiment Overview

| Field        | Value                                                                      |
|--------------|----------------------------------------------------------------------------|
| Goal         | Introduce RabbitMQ + asynchronous event flow for "Add Visit" and compare performance against Experiment 1 (synchronous baseline) at 100 / 500 / 1000 concurrent users |
| Branch       | `exp-3-eda` (created from `main-control`)                                 |
| Load Levels  | 100, 500, 1000 concurrent users                                            |
| Person       | Person B (SET Y)                                                           |

---

## Required Output Files

> **AGENT:** At the end of this experiment, verify all of the following files exist.

```text
results/experiment-3/
├── Exp3_JMeter_100.jtl
├── Exp3_JMeter_500.jtl
├── Exp3_JMeter_1000.jtl
├── Exp3_Summary_100/            ← JMeter HTML report directory
├── Exp3_Summary_500/
├── Exp3_Summary_1000/
└── Exp3_EDA_vs_Baseline.csv

screenshots/
├── Exp3_Grafana_1000_users.png    (CRITICAL)
└── Exp3_RabbitMQ_Dashboard.png
```

---

## Stage 1 — Create EDA Branch

> **AGENT:** Run these commands. Confirm the branch was created successfully
> before proceeding.

```bash
git checkout main-control
git checkout -b exp-3-eda
```

---

## Stage 2 — Add RabbitMQ to `docker-compose.yml`

> **AGENT:** This is a config edit. Guide the user to open `docker-compose.yml`
> at the project root and add the `rabbitmq` service block below.
> Do not remove or modify any existing services.

**File to edit:** `docker-compose.yml` (project root)

Add the following new service entry:

```yaml
rabbitmq:
  image: rabbitmq:3-management
  ports:
    - "5672:5672"
    - "15672:15672"
  healthcheck:
    test: ["CMD", "rabbitmqctl", "status"]
    interval: 30s
    timeout: 10s
    retries: 3
```

Rebuild and bring the full stack up:

```bash
./mvnw clean install -DskipTests
docker compose up -d
```

Verify RabbitMQ is running:

```bash
docker ps | grep rabbitmq
```

Open RabbitMQ Management UI in a browser: `http://localhost:15672`
- Default credentials: `guest` / `guest`

> **AGENT:** If the RabbitMQ UI is not reachable or the container is not running,
> STOP and alert the user before proceeding.

---

## Stage 3 — Refactor API Gateway — Producer (Synchronous → Event)

> **AGENT:** This stage has two parts: (A) add a Maven dependency, (B) modify a Java
> controller. Guide the user through both. The changes must be in the correct files.

### 3.A. Add AMQP Dependency to API Gateway

**File to edit:** `spring-petclinic-api-gateway/pom.xml`

Add inside the `<dependencies>` block:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### 3.B. Replace Direct REST Call with Event Publish

Find the `POST /owners/{ownerId}/pets/{petId}/visits` endpoint controller in the API Gateway module.

Inject `RabbitTemplate` and replace the direct synchronous REST call with an event publish:

```java
@Autowired
private RabbitTemplate rabbitTemplate;

@PostMapping("/owners/{ownerId}/pets/{petId}/visits")
public ResponseEntity<?> addVisit(@PathVariable Long ownerId,
                                  @PathVariable Long petId,
                                  @RequestBody Visit visit) {
    Map<String, Object> event = new HashMap<>();
    event.put("ownerId", ownerId);
    event.put("petId", petId);
    event.put("visitDate", visit.getDate().toString());
    event.put("description", visit.getDescription());

    rabbitTemplate.convertAndSend("petclinic.events", "VisitCreatedEvent", event);

    // Fire-and-forget: immediate 202
    return ResponseEntity.accepted().build();
}
```

> **Note:** The exchange name is `petclinic.events`, routing key is `VisitCreatedEvent`.
> This is a fire-and-forget pattern — the gateway returns HTTP `202 Accepted` immediately
> without waiting for the visit to be persisted.

---

## Stage 4 — Create Event Consumer in `visits-service`

> **AGENT:** This stage has three parts: (A) add dependency, (B) add RabbitMQ config,
> (C) create the listener class. All three must be done before rebuilding.

### 4.A. Add AMQP Dependency to visits-service

**File to edit:** `visits-service/pom.xml`

Add inside the `<dependencies>` block:

```xml
<dependency>
  <groupId>org.springframework.boot</groupId>
  <artifactId>spring-boot-starter-amqp</artifactId>
</dependency>
```

### 4.B. Configure RabbitMQ Connection in visits-service

**File to edit:** `visits-service/src/main/resources/application.yml`

Add the following (merge with existing content, do not duplicate `spring:` key if already present):

```yaml
spring:
  rabbitmq:
    host: rabbitmq
    port: 5672
    username: guest
    password: guest
```

### 4.C. Create `VisitEventListener.java`

Create a new file in the `visits-service` source tree:

```java
@Component
public class VisitEventListener {

    private final VisitRepository visitRepository;

    public VisitEventListener(VisitRepository visitRepository) {
        this.visitRepository = visitRepository;
    }

    @RabbitListener(queues = "visit.created.queue")
    public void handleVisitCreated(Map<String, Object> event) {
        Long ownerId = Long.parseLong(event.get("ownerId").toString());
        Long petId = Long.parseLong(event.get("petId").toString());
        LocalDate visitDate = LocalDate.parse(event.get("visitDate").toString());
        String description = (String) event.get("description");

        Visit visit = new Visit();
        visit.setDescription(description);
        visit.setDate(visitDate);
        // TODO: set owner/pet references if needed, depending on model
        visitRepository.save(visit);

        System.out.println("Async visit saved with ID: " + visit.getId());
    }
}
```

**Queue/Exchange mapping for this experiment:**

| Exchange           | Routing Key         | Queue                 |
|--------------------|---------------------|-----------------------|
| `petclinic.events` | `VisitCreatedEvent` | `visit.created.queue` |

> **Note:** Define queues and bindings via Spring `@Bean` declarations if needed
> for the exchange-to-queue routing to work.

---

## Stage 5 — Rebuild and Deploy EDA Version

> **AGENT:** Rebuild the full stack with the new code. Then validate the EDA flow
> works before running JMeter.

```bash
./mvnw clean install -DskipTests
docker compose up -d --build
```

**Validate the EDA flow:**

1. Send a test POST request:
   ```bash
   curl -X POST http://localhost:8080/api-gateway/owners/1/pets/1/visits \
     -H "Content-Type: application/json" \
     -d '{"description": "test visit", "date": "2025-01-01"}'
   ```
   **Expected response:** HTTP `202 Accepted`

2. Open RabbitMQ UI at `http://localhost:15672` → check the Queues tab.
   **Expected:** Messages being enqueued and dequeued on `visit.created.queue`.

> **AGENT:** If the POST returns something other than 202, or RabbitMQ shows no activity,
> stop and alert the user. The EDA refactor must be verified before running load tests.

---

## Stage 6 — JMeter Run at 100 Users

> **AGENT:**
> 1. Confirm Thread Group in `jmeter/petclinic_full_scenario.jmx` is set to **100 threads**.
>    If not, instruct the user to open JMeter GUI, change to 100, and re-save.
> 2. Open Grafana at http://localhost:3000 — dashboard should show CPU/memory for
>    gateway and visits-service. Have it ready before starting the run.

```bash
mkdir -p results/experiment-3

jmeter -n \
  -t jmeter/petclinic_full_scenario.jmx \
  -l results/experiment-3/Exp3_JMeter_100.jtl \
  -e -o results/experiment-3/Exp3_Summary_100/
```

Let the test run for at least **2–3 minutes** to stabilize.

Verify output:

```bash
ls -lh results/experiment-3/Exp3_JMeter_100.jtl
```

---

## Stage 7 — JMeter Run at 500 Users

> **AGENT:** Instruct the user to change Thread Group to **500 threads** in JMeter GUI
> and re-save before running.

Change Thread Group to **500 threads** and re-save the JMX, then run:

```bash
jmeter -n \
  -t jmeter/petclinic_full_scenario.jmx \
  -l results/experiment-3/Exp3_JMeter_500.jtl \
  -e -o results/experiment-3/Exp3_Summary_500/
```

Let the test run for at least **2–3 minutes**.

Verify output:

```bash
ls -lh results/experiment-3/Exp3_JMeter_500.jtl
```

---

## Stage 8 — JMeter Run at 1000 Users

> **AGENT:** Instruct the user to change Thread Group to **1000 threads** in JMeter GUI
> and re-save before running. This is the most critical run — the screenshot here is
> CRITICAL for the paper and must be taken during active load.

Change Thread Group to **1000 threads** and re-save the JMX, then run:

```bash
jmeter -n \
  -t jmeter/petclinic_full_scenario.jmx \
  -l results/experiment-3/Exp3_JMeter_1000.jtl \
  -e -o results/experiment-3/Exp3_Summary_1000/
```

Let the test run for at least **2–3 minutes**.

---

> ## 📸 SCREENSHOT REQUIRED — MANDATORY & CRITICAL
>
> **When:** During the **1000-user test** — while load is active and high.
>
> **What to capture:** Grafana dashboard showing **CPU / memory / HTTP metrics**
> for `spring-petclinic-api-gateway` and `visits-service` under EDA load at 1000 users.
>
> **Save as:** `screenshots/Exp3_Grafana_1000_users.png`
>
> **AGENT:** This is CRITICAL for the paper — it is the primary visual comparison point
> against Experiment 1's 1000-user screenshot. Display this reminder very prominently.
> Pause and wait for the user to confirm it has been taken before continuing.

---

Verify output:

```bash
ls -lh results/experiment-3/Exp3_JMeter_1000.jtl
```

---

## Stage 9 — Capture RabbitMQ Dashboard Screenshot

After all three JMeter runs are complete:

Open RabbitMQ Management UI → `http://localhost:15672` → **Queues** tab.

Observe queue lengths and activity for `visit.created.queue` and related exchanges.

---

> ## 📸 SCREENSHOT REQUIRED — MANDATORY
>
> **What to capture:** RabbitMQ Management UI — **Queues tab** showing queue lengths
> for `visit.created.queue` (and any related exchanges visible on screen).
>
> **Save as:** `screenshots/Exp3_RabbitMQ_Dashboard.png`
>
> **AGENT:** Pause and wait for the user to confirm the screenshot has been taken
> and saved before continuing.

---

## Stage 10 — Build EDA vs Baseline Comparison CSV

> **AGENT:** Guide the user through opening the HTML reports for both Experiment 1
> and Experiment 3, extracting the matching metrics, and populating the CSV.
> Note: Experiment 1 results were produced by Person A — ensure the data has been
> shared between the two persons before this step.

Open each HTML report and extract **Throughput (req/s)** and **p99 latency (ms)** for each run:

- Baseline (Exp 1): `results/experiment-1/Exp1_Summary_{N}/index.html`
- EDA (Exp 3): `results/experiment-3/Exp3_Summary_{N}/index.html`

Create `results/experiment-3/Exp3_EDA_vs_Baseline.csv`:

```text
Users,Baseline_p99_latency_ms,EDA_p99_latency_ms,Improvement_pct,Baseline_Throughput_req_sec,EDA_Throughput_req_sec
100,B_p99_100,E_p99_100,IMP_100,B_thr_100,E_thr_100
500,B_p99_500,E_p99_500,IMP_500,B_thr_500,E_thr_500
1000,B_p99_1000,E_p99_1000,IMP_1000,B_thr_1000,E_thr_1000
```

Replace all placeholder values with real measured numbers.

> **Formula for Improvement_pct:**
> ```
> Improvement_pct = (Baseline_p99 − EDA_p99) / Baseline_p99 * 100
> ```
> Choose this formula and stay consistent across all rows.

---

## Checkpoint — Experiment 3 Complete

> **AGENT:** Run through this checklist. Report any missing items before declaring
> Experiment 3 complete. Do not proceed to Experiment 4 until all items are checked.

- [ ] `results/experiment-3/Exp3_JMeter_100.jtl` — exists and non-empty
- [ ] `results/experiment-3/Exp3_JMeter_500.jtl` — exists and non-empty
- [ ] `results/experiment-3/Exp3_JMeter_1000.jtl` — exists and non-empty
- [ ] `results/experiment-3/Exp3_Summary_100/` — HTML report directory present
- [ ] `results/experiment-3/Exp3_Summary_500/` — HTML report directory present
- [ ] `results/experiment-3/Exp3_Summary_1000/` — HTML report directory present
- [ ] `results/experiment-3/Exp3_EDA_vs_Baseline.csv` — filled with real values
- [ ] `screenshots/Exp3_Grafana_1000_users.png` — taken during 1000-user run (CRITICAL)
- [ ] `screenshots/Exp3_RabbitMQ_Dashboard.png` — taken after tests

---

> **All items checked? Proceed to `Experiment_4_Saga_Data_Consistency.md`.**

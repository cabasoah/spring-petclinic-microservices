# CS6075_PRJ-1 — Experiment 2: Chaos Engineering & Resilience (Synchronous)

## Assigned to: Person A — SET X

> **Prerequisites:**
> - `CS6075_COMMON_Setup.md` must be fully completed.
> - `Experiment_1_Baseline_Scalability.md` must be fully completed — Experiment 2 builds
>   on the same environment and same branch lineage.
> - All Experiment 1 output files and screenshots must exist.

---

## AI-CODER INSTRUCTIONS — READ FIRST

```
AGENT BEHAVIOR RULES (enforce throughout this experiment):

1. IDENTITY CHECK: Confirm the user is Person A (SET X) before proceeding.
   If they are Person B, stop and redirect them to Experiment_4.md.

2. PRE-FLIGHT CHECK: Before starting, verify:
   - Experiment 1 checkpoint is fully complete (all .jtl, CSV, screenshots present)
   - jmeter/petclinic_full_scenario.jmx exists
   - Docker Desktop is running with 8 GB RAM allocated
   - The baseline stack is currently DOWN (docker compose down was run after Exp 1)
   Report any failures before proceeding.

3. MULTI-TERMINAL COORDINATION: This experiment requires actions in two separate
   terminals simultaneously. Clearly label which commands go in Terminal A (JMeter)
   and which go in Terminal B (chaos injection). Remind the user to open both terminals
   before starting Stage 4.

4. STAGE-BY-STAGE EXECUTION: Complete each section fully before moving on.
   Pay special attention to timing in Stage 4 — the chaos sequence is time-sensitive.

5. SCREENSHOT REMINDERS: Whenever a screenshot is required, STOP execution
   and display a prominent reminder to the user. Screenshots here are CRITICAL for
   the paper — they are the primary visual evidence of outage and recovery.

6. DO NOT RENAME FILES. All filenames and paths are exact and mandatory.
```

---

## Experiment Overview

| Field       | Value                                                                  |
|-------------|------------------------------------------------------------------------|
| Goal        | Under a steady load of 500 users, shut down `visits-service` and observe how the circuit breaker + automatic restart behaves — error % vs. fallback, and recovery time |
| Branch      | `exp-2-chaos` (created from `main-control`)                           |
| Load Level  | 500 concurrent users (fixed for this experiment)                       |
| Person      | Person A (SET X)                                                       |

---

## Required Output Files

> **AGENT:** At the end of this experiment, verify all of the following files exist.

```text
results/experiment-2/
├── Exp2_JMeter_Chaos.jtl
├── Exp2_Chaos_Summary/          ← JMeter HTML report directory
├── Exp2_Chaos_Summary.csv
└── Exp2_CircuitBreaker_States.log

screenshots/
├── Exp2_Grafana_Outage.png         (CRITICAL)
└── Exp2_CircuitBreaker_States.png  (CRITICAL)
```

---

## Stage 1 — Create the Chaos Branch

> **AGENT:** Run these commands. Confirm the branch was created successfully
> (`git branch` should show `* exp-2-chaos`) before proceeding.

```bash
git checkout main-control
git checkout -b exp-2-chaos
```

---

## Stage 2 — Configure Aggressive Circuit Breaker

> **AGENT:** This is a code edit. Guide the user to open the file at the exact path below,
> find or create the `resilience4j` section, and apply the configuration exactly as shown.
> Do not add or remove any fields.

**File to edit:**
```text
spring-petclinic-api-gateway/src/main/resources/application.yml
```

Find or create the `resilience4j.circuitbreaker.instances` block and ensure it contains exactly:

```yaml
resilience4j:
  circuitbreaker:
    instances:
      visits:
        slidingWindowSize: 10
        minimumNumberOfCalls: 5
        permittedNumberOfCallsInHalfOpenState: 3
        waitDurationInOpenState: 10s
        failureRateThreshold: 50
        slowCallRateThreshold: 50
        slowCallDurationThreshold: 2s
```

Rebuild only the gateway module (faster than full rebuild):

```bash
./mvnw clean install -pl spring-petclinic-api-gateway -am -DskipTests
docker compose up -d --build
```

> **AGENT:** After the build, verify the API Gateway container is running:
> ```bash
> docker ps | grep api-gateway
> curl -I http://localhost:8080/api-gateway/
> ```
> Expected: HTTP 200 or 302. If not, stop and alert the user.

---

## Stage 3 — Add Restart Policy and Healthcheck for `visits-service`

> **AGENT:** This is another config edit. Guide the user to open `docker-compose.yml`
> at the project root, find the `visits-service` service block, and add the two fields
> below (`restart` and `healthcheck`). Do not remove any existing fields.

**File to edit:** `docker-compose.yml` (project root)

Under the `visits-service` service, add:

```yaml
visits-service:
  # (keep all existing image/build/env config unchanged)
  restart: on-failure
  healthcheck:
    test: ["CMD", "curl", "-f", "http://localhost:9966/actuator/health"]
    interval: 30s
    timeout: 10s
    retries: 3
```

Apply the change:

```bash
docker compose up -d --build visits-service
```

Verify the service is running and healthy:

```bash
docker ps
curl -I http://localhost:8080/api-gateway/
```

> **AGENT:** Both commands must succeed before proceeding to Stage 4.
> If `visits-service` is not healthy, stop and notify the user.

---

## Stage 4 — Run JMeter at 500 Users + Inject Chaos

> **AGENT:** This stage requires TWO terminals open simultaneously.
> Before starting, remind the user:
> "Open TWO terminal windows now. Terminal A will run JMeter. Terminal B will
>  inject the failure at the right moment. Have both ready before proceeding."
>
> Also remind the user to have Grafana open in a browser BEFORE starting:
> - URL: http://localhost:3000
> - Show CPU/memory AND HTTP metrics for `visits-service` and `api-gateway`
>
> The chaos sequence is time-sensitive. Follow the timing carefully.

### Pre-chaos Setup

Open Grafana at `http://localhost:3000`.  
Display CPU/memory and HTTP metrics for `visits-service` and `api-gateway`.

Ensure Thread Group in `jmeter/petclinic_full_scenario.jmx` is set to **500 threads**.
If it was left at a different value from Experiment 1, change it to 500 and re-save.

### Terminal A — Start 500-User Steady Load

```bash
mkdir -p results/experiment-2

jmeter -n \
  -t jmeter/petclinic_full_scenario.jmx \
  -l results/experiment-2/Exp2_JMeter_Chaos.jtl \
  -e -o results/experiment-2/Exp2_Chaos_Summary/
```

Wait approximately **90 seconds** for the load to reach a steady state.

### Terminal B — Inject Failure (at ~90 seconds)

After 90 seconds of stable load, in Terminal B:

```bash
docker stop visits-service
```

Keep JMeter **running** in Terminal A during the outage — approximately **45 seconds**.  
Watch Grafana during this period.

---

> ## 📸 SCREENSHOT REQUIRED — MANDATORY
>
> **When:** During the outage — while `visits-service` is stopped and JMeter is still running.
>
> **What to capture:** Grafana panels showing a **clear drop or outage** for `visits-service`
> AND visible **error spikes** in HTTP metrics.
>
> **Save as:** `screenshots/Exp2_Grafana_Outage.png`
>
> **AGENT:** Display this reminder prominently. This screenshot is CRITICAL evidence
> of the chaos event for the paper. Pause and wait for the user to confirm it has been
> taken before continuing.

---

### Restart the Service (at ~135 seconds)

After ~45 seconds of outage, in Terminal B:

```bash
docker start visits-service
```

Let JMeter continue running for another **120 seconds** to capture the recovery phase.

Then stop JMeter (Ctrl+C in Terminal A if it has not auto-exited).

---

## Stage 5 — Capture Circuit Breaker State Transitions

> **AGENT:** Run this command immediately after stopping JMeter so that the gateway
> log still contains the circuit breaker transitions. Save the output file.

```bash
docker logs spring-petclinic-api-gateway \
  | grep -i "circuitbreaker" \
  | tail -n 100 > results/experiment-2/Exp2_CircuitBreaker_States.log
```

Open the log file and visually confirm the state transitions are present in sequence:

```text
CLOSED → OPEN → HALF_OPEN → CLOSED
```

> **AGENT:** If the log file is empty or does not contain state transitions,
> alert the user: "Circuit breaker states not found in logs. The circuit breaker
> may not have triggered. Verify the resilience4j config was applied correctly
> and the service was stopped long enough to cross the failure threshold."

---

> ## 📸 SCREENSHOT REQUIRED — MANDATORY
>
> **What to capture:** The terminal or text editor showing the log file contents,
> with the state transitions `CLOSED → OPEN → HALF_OPEN → CLOSED` visible in sequence.
>
> **Save as:** `screenshots/Exp2_CircuitBreaker_States.png`
>
> **AGENT:** Display this reminder prominently. Pause and wait for the user to confirm
> the screenshot has been taken and saved before continuing.

---

## Stage 6 — Build Chaos Summary CSV

> **AGENT:** Guide the user through opening the HTML report or .jtl file to identify
> the three time windows. The windows correspond to the chaos timeline executed in Stage 4.

Open `results/experiment-2/Exp2_Chaos_Summary/index.html` in a browser,  
or open `Exp2_JMeter_Chaos.jtl` in JMeter GUI via **Summary Report**.

Identify and record metrics for these 3 time windows:

| Phase           | Time Window                                               |
|-----------------|-----------------------------------------------------------|
| Pre-chaos       | ~90 seconds before `docker stop` was run                 |
| During outage   | From `docker stop` until just before `docker start`      |
| Recovery        | ~120 seconds after `docker start`                        |

For each phase, extract:
- **Throughput** (req/s)
- **p99 latency** (ms)
- **Error %**

Create `results/experiment-2/Exp2_Chaos_Summary.csv` with this exact format:

```text
Phase,Throughput_req_sec,p99_latency_ms,Error_percent
Pre-chaos (90s),T1,L1,E1
During outage (45s),T2,L2,E2
Recovery (120s),T3,L3,E3
```

Replace `T1`, `L1`, `E1` etc. with the actual measured values.

---

## Checkpoint — Experiment 2 Complete

> **AGENT:** Run through this checklist. Report any missing items before declaring
> Experiment 2 complete.

- [ ] `results/experiment-2/Exp2_JMeter_Chaos.jtl` — exists and non-empty
- [ ] `results/experiment-2/Exp2_Chaos_Summary/` — HTML report directory present
- [ ] `results/experiment-2/Exp2_Chaos_Summary.csv` — filled with real values for 3 phases
- [ ] `results/experiment-2/Exp2_CircuitBreaker_States.log` — contains CLOSED/OPEN/HALF_OPEN transitions
- [ ] `screenshots/Exp2_Grafana_Outage.png` — taken during outage (CRITICAL)
- [ ] `screenshots/Exp2_CircuitBreaker_States.png` — shows state transition log (CRITICAL)

---

> **All items checked?**
>
> Person A (SET X) is now done with both experiments.
>
> Coordinate with Person B to proceed to Final Artifact Assembly
> as described in `CS6075_COMMON_Setup.md` → Section 5.

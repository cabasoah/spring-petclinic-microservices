# CS6075_PRJ-1 — Experiment 1: Baseline Scalability (Synchronous)

## Assigned to: Person A — SET X

> **Prerequisites:** You must have completed ALL steps in `CS6075_COMMON_Setup.md` before
> starting this file — including tool verification, repo clone, baseline build, and JMeter
> test plan creation. Do not begin here without confirming those are done.

---

## AI-CODER INSTRUCTIONS — READ FIRST

```
AGENT BEHAVIOR RULES (enforce throughout this experiment):

1. IDENTITY CHECK: Confirm the user is Person A (SET X) before proceeding.
   If they are Person B, stop and redirect them to Experiment_3.md.

2. PRE-FLIGHT CHECK: Before starting, verify:
   - jmeter/petclinic_full_scenario.jmx exists
   - Docker Desktop is running with 8 GB RAM allocated
   - The baseline stack can be brought up cleanly (main-control branch)
   Report any failures before proceeding.

3. STAGE-BY-STAGE EXECUTION: Complete each section fully before moving on.
   Confirm each JMeter run produces its output files before starting the next.

4. SCREENSHOT REMINDERS: Whenever a screenshot is required, STOP execution
   and display a prominent reminder to the user. Wait for acknowledgment
   before continuing to the next step.

5. DO NOT RENAME FILES. All filenames and paths are exact and mandatory.
```

---

## Experiment Overview

| Field        | Value                                                         |
|--------------|---------------------------------------------------------------|
| Goal         | Measure how stock synchronous Spring PetClinic behaves under 100, 500, and 1000 concurrent users — throughput, p99 latency, error % |
| Branch       | `main-control` — no structural code changes for this experiment |
| Load Levels  | 100, 500, 1000 concurrent users                               |
| Person       | Person A (SET X)                                              |

---

## Required Output Files

> **AGENT:** At the start of each stage, verify the expected output files do not already
> exist (to avoid overwriting). At the end, confirm all files were produced.

```text
results/experiment-1/
├── Exp1_JMeter_100.jtl
├── Exp1_JMeter_500.jtl
├── Exp1_JMeter_1000.jtl
├── Exp1_Summary_100/            ← JMeter HTML report directory
├── Exp1_Summary_500/
├── Exp1_Summary_1000/
├── Exp1_Baseline_Summary_Table.csv
└── Exp1_Summary_CSVs.zip

screenshots/
├── Exp1_Grafana_100_users.png
├── Exp1_Grafana_500_users.png
└── Exp1_Grafana_1000_users.png
```

> Note: `screenshots/Exp1_JMeter_Plan.png` was already taken during JMeter setup in the Common file.

---

## Stage 1 — Start the Baseline Stack

> **AGENT:** Run these commands in order. After `docker compose up -d`, wait 2 minutes,
> then run the verification command. Only proceed to Stage 2 if the gateway responds.

```bash
git checkout main-control
./mvnw clean install -DskipTests
docker compose up -d
```

Wait approximately **2 minutes** for services to initialize.

Verify the stack is healthy:

```bash
curl -I http://localhost:8080/api-gateway/
```

**Expected:** HTTP `200` or `302` response.

> **AGENT:** If the response is not 200/302, STOP and notify the user:
> "The API Gateway is not responding. Check docker ps and container logs before continuing."

Also confirm Grafana is reachable (needed for screenshots):

```bash
curl -I http://localhost:3000
```

Open Grafana in a browser: `http://localhost:3000`
- Default login: `admin` / `admin` (unless changed)
- Open the dashboard showing **CPU and memory** for:
  - `spring-petclinic-api-gateway`
  - `visits-service`
  - (and ideally other services)

> **AGENT:** Remind the user to have Grafana open and the correct dashboard loaded
> BEFORE starting any JMeter run. Screenshots must be taken during live load.

---

## Stage 2 — JMeter Run at 100 Users

> **AGENT:** Before running:
> 1. Confirm Thread Group in `jmeter/petclinic_full_scenario.jmx` is set to **100 threads**.
>    If not, instruct the user to open JMeter GUI, change threads to 100, and re-save.
> 2. Create the output directory.
> 3. Start the JMeter run.
> 4. At ~60 seconds in, STOP and prompt for the screenshot.

```bash
mkdir -p results/experiment-1

jmeter -n \
  -t jmeter/petclinic_full_scenario.jmx \
  -l results/experiment-1/Exp1_JMeter_100.jtl \
  -e -o results/experiment-1/Exp1_Summary_100/
```

Let the test run for at least **2–3 minutes** to allow metrics to stabilize.

---

> ## 📸 SCREENSHOT REQUIRED — MANDATORY
>
> **When:** At approximately **60 seconds** into the JMeter run (while load is active).
>
> **What to capture:** Grafana dashboard showing **CPU and memory** metrics for
> `spring-petclinic-api-gateway` and `visits-service` during the 100-user load.
>
> **Save as:** `screenshots/Exp1_Grafana_100_users.png`
>
> **AGENT:** Display this reminder prominently. Pause and wait for the user to confirm
> the screenshot has been taken and saved before continuing.

---

Stop JMeter after 2–3 minutes if it has not auto-exited. Verify output file exists:

```bash
ls -lh results/experiment-1/Exp1_JMeter_100.jtl
ls -lh results/experiment-1/Exp1_Summary_100/
```

---

## Stage 3 — JMeter Run at 500 Users

> **AGENT:**
> 1. Instruct the user to open JMeter GUI, change Thread Group threads to **500**, and re-save
>    `jmeter/petclinic_full_scenario.jmx`.
> 2. Confirm the save before running the CLI command below.

Change Thread Group to **500 threads** in JMeter GUI and re-save the JMX, then run:

```bash
jmeter -n \
  -t jmeter/petclinic_full_scenario.jmx \
  -l results/experiment-1/Exp1_JMeter_500.jtl \
  -e -o results/experiment-1/Exp1_Summary_500/
```

Let the test run for at least **2–3 minutes**.

---

> ## 📸 SCREENSHOT REQUIRED — MANDATORY
>
> **When:** During peak load — approximately **60 seconds** into the run,
> around the end of the ramp-up period.
>
> **What to capture:** Grafana dashboard showing CPU/memory for gateway and visits-service
> under 500-user load.
>
> **Save as:** `screenshots/Exp1_Grafana_500_users.png`
>
> **AGENT:** Display this reminder prominently. Pause and wait for the user to confirm
> the screenshot has been taken and saved before continuing.

---

Verify output:

```bash
ls -lh results/experiment-1/Exp1_JMeter_500.jtl
ls -lh results/experiment-1/Exp1_Summary_500/
```

---

## Stage 4 — JMeter Run at 1000 Users

> **AGENT:**
> 1. Instruct the user to open JMeter GUI, change Thread Group threads to **1000**, and re-save.
> 2. Confirm the save before running the CLI command below.
> 3. This is the most critical run. Alert the user that CPU/memory spikes are expected and
>    the screenshot during this run is CRITICAL for the paper.

Change Thread Group to **1000 threads** in JMeter GUI and re-save the JMX, then run:

```bash
jmeter -n \
  -t jmeter/petclinic_full_scenario.jmx \
  -l results/experiment-1/Exp1_JMeter_1000.jtl \
  -e -o results/experiment-1/Exp1_Summary_1000/
```

Let the test run for at least **2–3 minutes**.

---

> ## 📸 SCREENSHOT REQUIRED — MANDATORY & CRITICAL
>
> **When:** During **maximum load** — approximately **1–2 minutes** into the run,
> when CPU/memory spikes are visible.
>
> **What to capture:** Grafana dashboard showing peak CPU and memory usage for
> `spring-petclinic-api-gateway` and `visits-service` under 1000-user load.
>
> **Save as:** `screenshots/Exp1_Grafana_1000_users.png`
>
> **AGENT:** This screenshot is CRITICAL for the paper. Display this reminder very
> prominently. Pause and wait for the user to confirm it has been taken before continuing.

---

Verify output:

```bash
ls -lh results/experiment-1/Exp1_JMeter_1000.jtl
ls -lh results/experiment-1/Exp1_Summary_1000/
```

---

## Stage 5 — Build Summary CSV

> **AGENT:** Guide the user through extracting the three metrics from each run.
> Open each HTML dashboard or the .jtl file in JMeter GUI to find the values.

Open each HTML report in a browser and extract the following for each user count:

- **Throughput** (requests/second)
- **99th percentile latency** (ms) — labeled "99% Line" in JMeter
- **Error %**

Sources to open:
```text
results/experiment-1/Exp1_Summary_100/index.html
results/experiment-1/Exp1_Summary_500/index.html
results/experiment-1/Exp1_Summary_1000/index.html
```

Alternatively, open each `.jtl` file using **JMeter GUI → Summary Report**.

Create the file `results/experiment-1/Exp1_Baseline_Summary_Table.csv` with this exact format:

```text
Users,Throughput_req_sec,p99_latency_ms,Error_percent
100,XXX,YYY,ZZZ
500,AAA,BBB,CCC
1000,DDD,EEE,FFF
```

Replace `XXX`, `YYY`, `ZZZ` etc. with the actual measured values.

---

## Stage 6 — Zip Summary Outputs

```bash
cd results/experiment-1
zip -r Exp1_Summary_CSVs.zip \
  Exp1_Baseline_Summary_Table.csv \
  Exp1_Summary_100/ \
  Exp1_Summary_500/ \
  Exp1_Summary_1000/
cd ../..
```

Verify the zip was created:

```bash
ls -lh results/experiment-1/Exp1_Summary_CSVs.zip
```

---

## Checkpoint — Experiment 1 Complete

> **AGENT:** Run through this checklist. Report any missing items before declaring
> Experiment 1 complete. Do not proceed to Experiment 2 until all items are checked.

- [ ] `results/experiment-1/Exp1_JMeter_100.jtl` — exists and non-empty
- [ ] `results/experiment-1/Exp1_JMeter_500.jtl` — exists and non-empty
- [ ] `results/experiment-1/Exp1_JMeter_1000.jtl` — exists and non-empty
- [ ] `results/experiment-1/Exp1_Summary_100/` — HTML report directory present
- [ ] `results/experiment-1/Exp1_Summary_500/` — HTML report directory present
- [ ] `results/experiment-1/Exp1_Summary_1000/` — HTML report directory present
- [ ] `results/experiment-1/Exp1_Baseline_Summary_Table.csv` — filled with real values
- [ ] `results/experiment-1/Exp1_Summary_CSVs.zip` — created
- [ ] `screenshots/Exp1_Grafana_100_users.png` — taken during 100-user run
- [ ] `screenshots/Exp1_Grafana_500_users.png` — taken during 500-user run
- [ ] `screenshots/Exp1_Grafana_1000_users.png` — taken during 1000-user run (CRITICAL)

---

> **All items checked? Proceed to `Experiment_2_Chaos_Resilience.md`.**

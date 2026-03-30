# CS6075_PRJ-1 — COMMON SETUP & SHARED REFERENCE
## Given to: BOTH Person A (SET X) and Person B (SET Y)

> **This file is the shared foundation for all four experiments.**
> Every step in this file must be completed before running any experiment.
> Do not skip steps. Do not rename files. Follow literally.

---

## AI-CODER INSTRUCTIONS — READ FIRST

```
AGENT BEHAVIOR RULES (enforce throughout this file):
1. At the very start, ASK THE USER: "Are you Person A (SET X) or Person B (SET Y)?"
   - Person A (SET X) → will run Experiment 1 and Experiment 2
   - Person B (SET Y) → will run Experiment 3 and Experiment 4
   - Both persons receive THIS file + their 2 experiment files
   - Store the answer and refer to it whenever person-specific context is needed.

2. Before executing any section, VERIFY that all prerequisite tools are installed
   (see Section 2.A — run the version-check commands and confirm output).

3. REMIND THE USER with prominent text whenever a SCREENSHOT is required.
   Screenshots are mandatory evidence — never silently skip them.

4. Proceed STAGE BY STAGE. Do not jump ahead. Confirm each stage succeeds
   before moving to the next.
```

---

## 0. Project Overview

- **Group:** CS6075_PRJ-1
- **Topic:** Micro-Services Architecture Evaluation
- **Base Application:** [spring-petclinic-microservices](https://github.com/spring-petclinic/spring-petclinic-microservices)
- **Experiments:** 4 total | **Load Levels:** 100, 500, 1000 concurrent users

| ID           | Name                                | Type         | Assigned To |
|--------------|-------------------------------------|--------------|-------------|
| Experiment 1 | Baseline Scalability                | Synchronous  | Person A    |
| Experiment 2 | Chaos Engineering & Resilience      | Synchronous  | Person A    |
| Experiment 3 | EDA Micro-Refactor                  | Asynchronous | Person B    |
| Experiment 4 | Saga Pattern & Data Consistency     | Asynchronous | Person B    |

### Work Split

| Person   | Set   | Receives Files                                   |
|----------|-------|--------------------------------------------------|
| Person A | SET X | This file + Experiment_1.md + Experiment_2.md    |
| Person B | SET Y | This file + Experiment_3.md + Experiment_4.md    |

Each person works on their own laptop with their own Docker, JMeter, etc., and produces their own result files and screenshots.

### Final Submission

At the end, all artifacts (code + CSVs + screenshots + logs) are copied into `final-artifact/` (no `.git` folder inside) and compressed into one ZIP for submission.

---

## 1. Global Conventions

### 1.A. Project Root

All paths in ALL files are relative to:

```text
~/spring-petclinic-microservices
```

### 1.B. Git Branches

| Branch         | Purpose                                   |
|----------------|-------------------------------------------|
| `main-control` | Clean baseline from `main`                |
| `exp-2-chaos`  | Experiment 2 — Chaos Engineering          |
| `exp-3-eda`    | Experiment 3 — EDA Micro-Refactor         |
| `exp-4-saga`   | Experiment 4 — Saga (on top of EDA)       |

> **Rule:** Never modify `main` directly. All work is attached to `main-control` and experiment branches.

### 1.C. Required Folder Structure

Create this inside the project root (both persons):

```text
~/spring-petclinic-microservices/
├── jmeter/                   # JMeter test plans
├── results/
│   ├── experiment-1/         # Person A
│   ├── experiment-2/         # Person A
│   ├── experiment-3/         # Person B
│   └── experiment-4/         # Person B
├── screenshots/              # All screenshots go here (all experiments)
└── final-artifact/           # Created only at the very end, for submission
```

All output files (CSV, JTL, TXT, PNG) must go under `results/` and `screenshots/` exactly as specified in each experiment file.

### 1.D. Load Levels

```text
Concurrent user levels: 100 / 500 / 1000
```

| Experiment   | Load Detail                                                   |
|--------------|---------------------------------------------------------------|
| Experiment 1 | Synchronous baseline — tested at 100 / 500 / 1000 users       |
| Experiment 2 | Chaos run uses 500 users as the fixed steady load             |
| Experiment 3 | EDA refactor — tested at 100 / 500 / 1000 users               |
| Experiment 4 | 100 "Add Visit" operations to verify Saga behavior (not high-load) |

---

## 2. Prerequisites — Both Persons Must Complete This

> **AGENT:** Run version-check commands below. If any tool is missing or version is wrong,
> STOP and notify the user before proceeding. Do not continue with a broken environment.

### 2.A. Required Software

Each laptop must have all of the following installed:

| Tool              | Minimum Version    | Notes                                   |
|-------------------|--------------------|-----------------------------------------|
| Docker Desktop    | Any modern version | **Set RAM to 8 GB:** Settings → Resources → Memory |
| Docker Compose    | v2+                | Comes with modern Docker Desktop        |
| Java              | 17+                |                                         |
| Apache Maven      | 3.8+               |                                         |
| Apache JMeter     | 5.6+               | CLI and GUI both needed                 |
| Browser           | Any modern         | Chrome / Firefox / Edge                 |
| IDE / Text Editor | Any                | VS Code, IntelliJ, etc.                 |

### 2.B. Verify All Tool Versions

Run and confirm all of these succeed without errors:

```bash
java -version
mvn -version
docker --version
docker compose version
jmeter --version
```

> **AGENT:** Check the output of each command. If any fails or returns a version below minimum,
> STOP and inform the user: "Tool [X] is missing or outdated. Please install/upgrade before continuing."

---

## 3. Clone and Prepare Repository

> **AGENT:** This section must be run on BOTH laptops. Confirm with the user that they are
> starting fresh, then execute these steps in order.

### 3.A. Clone the Repository

```bash
git clone https://github.com/spring-petclinic/spring-petclinic-microservices.git
cd spring-petclinic-microservices
```

### 3.B. Create `main-control` Branch

```bash
git checkout main
git branch -D main-control 2>/dev/null || true
git checkout -b main-control
```

> **Rule:** `main-control` is the clean baseline. It is never directly modified.
> All experiments branch off from `main-control`.

### 3.C. Build and Verify the Baseline Stack

Run once to confirm everything compiles and the stack comes up:

```bash
./mvnw clean install -DskipTests
docker compose up -d
```

Wait approximately **2 minutes**, then verify health:

```bash
curl -I http://localhost:8080/api-gateway/
curl -I http://localhost:3000        # Prometheus/Grafana stack, if present
docker ps
```

**Expected:** API Gateway returns HTTP `200` or `302` with an HTML page = stack is healthy.

Stop the stack when done (to free resources before experiments):

```bash
docker compose down
```

> **AGENT:** If `curl` returns a non-2xx/3xx response or `docker ps` shows unhealthy containers,
> STOP and notify the user: "Baseline stack verification failed. Do not proceed with experiments
> until the stack is healthy."

---

## 4. Shared JMeter Test Plan

> One single JMeter test plan is used across ALL four experiments:
> `jmeter/petclinic_full_scenario.jmx`
>
> **AGENT:** This test plan must be created ONCE and saved before any experiment begins.
> Both persons use the same plan. Confirm the file exists at the correct path before
> proceeding to any experiment.

### 4.A. Create the Test Plan

1. Open **JMeter GUI**.
2. Create a new **Test Plan** → Name it: `PetClinic Full Scenario`
3. Right-click Test Plan → **Add → Threads (Users) → Thread Group**:
   - Name: `MainUserLoad`
   - Number of Threads (users): `100` ← starting default; you will change this per run
   - Ramp-up Period: `30` seconds
   - Loop Count: `forever` (or `10000`; the CLI run duration will control when to stop)
4. Add **HTTP Request Defaults** (under Thread Group):
   - Server Name or IP: `localhost`
   - Port Number: `8080`
5. Add **HTTP Cookie Manager** (under Thread Group) to handle sessions.

### 4.B. User Journey HTTP Requests

Add these **4 HTTP Samplers** under the Thread Group, in this exact order:

| # | Method | Path                                      | Notes                                              |
|---|--------|-------------------------------------------|----------------------------------------------------|
| 1 | GET    | `/api-gateway/`                           | Homepage                                           |
| 2 | GET    | `/api-gateway/owners?lastName=`           | Owner search — `lastName` empty or set to `Davis`; keep consistent |
| 3 | POST   | `/api-gateway/owners/1/pets/1/visits`     | Body: JSON with `description` + `date` fields      |
| 4 | GET    | `/api-gateway/`                           | Same as #1 — repeat homepage hit                   |

### 4.C. Summary Report Listener

Add **Listener → Summary Report** under the Thread Group.

This listener must be configured to capture:

- Throughput (req/s)
- Average latency (ms)
- 90th percentile latency (ms)
- 99th percentile latency (ms)
- Error %

### 4.D. Save the Test Plan

Save the file to exactly this path:

```text
jmeter/petclinic_full_scenario.jmx
```

---

> ## 📸 SCREENSHOT REQUIRED
>
> **What:** JMeter GUI showing the full test plan tree:
> `Test Plan → Thread Group (MainUserLoad) → 4 HTTP Samplers → Summary Report`
>
> **Save as:** `screenshots/Exp1_JMeter_Plan.png`
>
> This screenshot is mandatory artifact evidence.

---

> **AGENT:** Confirm the file `jmeter/petclinic_full_scenario.jmx` was saved before
> telling the user to proceed to their experiment files.

---

## 5. Final Artifact Assembly (Both Persons — Done at the Very End)

> **AGENT:** This section is executed ONLY after BOTH persons have completed ALL their
> experiments and collected all result files and screenshots. Do not run this section early.

**Target ZIP filename:** `CS6075_PRJ1_Experiments_Artifact.zip`

### 5.A. Create `final-artifact/` Directory Structure

One person coordinates this step (typically by transferring files from both machines):

```bash
mkdir -p final-artifact/results
mkdir -p final-artifact/screenshots
```

### 5.B. Copy Artifacts from Person A (SET X)

```bash
cp -r results/experiment-1 final-artifact/results/
cp -r results/experiment-2 final-artifact/results/
cp -r screenshots/Exp1_* final-artifact/screenshots/
cp -r screenshots/Exp2_* final-artifact/screenshots/

git checkout exp-2-chaos
cp -r . final-artifact/exp-2-chaos
rm -rf final-artifact/exp-2-chaos/.git
```

### 5.C. Copy Artifacts from Person B (SET Y)

```bash
cp -r results/experiment-3 final-artifact/results/
cp -r results/experiment-4 final-artifact/results/
cp -r screenshots/Exp3_* final-artifact/screenshots/
cp -r screenshots/Exp4_* final-artifact/screenshots/

git checkout exp-4-saga
cp -r . final-artifact/exp-4-saga
rm -rf final-artifact/exp-4-saga/.git
```

### 5.D. Copy JMeter Test Plan

```bash
mkdir -p final-artifact/jmeter
cp jmeter/petclinic_full_scenario.jmx final-artifact/jmeter/
```

### 5.E. Verify Final Layout

The `final-artifact/` directory must look exactly like this:

```text
final-artifact/
├── jmeter/
│   └── petclinic_full_scenario.jmx
├── results/
│   ├── experiment-1/
│   ├── experiment-2/
│   ├── experiment-3/
│   └── experiment-4/
├── screenshots/
│   ├── Exp1_JMeter_Plan.png
│   ├── Exp1_Grafana_100_users.png
│   ├── Exp1_Grafana_500_users.png
│   ├── Exp1_Grafana_1000_users.png
│   ├── Exp2_Grafana_Outage.png
│   ├── Exp2_CircuitBreaker_States.png
│   ├── Exp3_Grafana_1000_users.png
│   ├── Exp3_RabbitMQ_Dashboard.png
│   ├── Exp4_RabbitMQ_VisitFailedQueue.png
│   └── Exp4_DB_Query.png
├── exp-2-chaos/              # Full code state for Experiments 1 & 2
└── exp-4-saga/               # Full code state for Experiments 3 & 4
```

### 5.F. Zip for Submission

```bash
cd final-artifact
zip -r ../CS6075_PRJ1_Experiments_Artifact.zip .
cd ..
```

**Verify the ZIP:**
- File size is under 500 MB
- After extracting in a fresh directory, all expected files are present
- `docker compose up -d` runs cleanly from both `exp-2-chaos/` and `exp-4-saga/`

---

## 6. Full Submission Checklist

> **AGENT:** Run through this checklist at the end. Report which items are complete and
> which are missing. Do not declare the submission ready until all boxes are checked.

### Experiment 1 (Person A)
- [ ] `results/experiment-1/Exp1_JMeter_100.jtl`
- [ ] `results/experiment-1/Exp1_JMeter_500.jtl`
- [ ] `results/experiment-1/Exp1_JMeter_1000.jtl`
- [ ] `results/experiment-1/Exp1_Summary_100/` (JMeter HTML report)
- [ ] `results/experiment-1/Exp1_Summary_500/`
- [ ] `results/experiment-1/Exp1_Summary_1000/`
- [ ] `results/experiment-1/Exp1_Baseline_Summary_Table.csv`
- [ ] `results/experiment-1/Exp1_Summary_CSVs.zip`
- [ ] `screenshots/Exp1_JMeter_Plan.png`
- [ ] `screenshots/Exp1_Grafana_100_users.png`
- [ ] `screenshots/Exp1_Grafana_500_users.png`
- [ ] `screenshots/Exp1_Grafana_1000_users.png`

### Experiment 2 (Person A)
- [ ] `results/experiment-2/Exp2_JMeter_Chaos.jtl`
- [ ] `results/experiment-2/Exp2_Chaos_Summary/` (JMeter HTML report)
- [ ] `results/experiment-2/Exp2_Chaos_Summary.csv`
- [ ] `results/experiment-2/Exp2_CircuitBreaker_States.log`
- [ ] `screenshots/Exp2_Grafana_Outage.png`
- [ ] `screenshots/Exp2_CircuitBreaker_States.png`

### Experiment 3 (Person B)
- [ ] `results/experiment-3/Exp3_JMeter_100.jtl`
- [ ] `results/experiment-3/Exp3_JMeter_500.jtl`
- [ ] `results/experiment-3/Exp3_JMeter_1000.jtl`
- [ ] `results/experiment-3/Exp3_Summary_100/`
- [ ] `results/experiment-3/Exp3_Summary_500/`
- [ ] `results/experiment-3/Exp3_Summary_1000/`
- [ ] `results/experiment-3/Exp3_EDA_vs_Baseline.csv`
- [ ] `screenshots/Exp3_Grafana_1000_users.png`
- [ ] `screenshots/Exp3_RabbitMQ_Dashboard.png`

### Experiment 4 (Person B)
- [ ] `results/experiment-4/Exp4_JMeter_100.jtl`
- [ ] `results/experiment-4/Exp4_Summary_100/`
- [ ] `results/experiment-4/Exp4_Saga_Data_Integrity.txt`
- [ ] `screenshots/Exp4_RabbitMQ_VisitFailedQueue.png`
- [ ] `screenshots/Exp4_DB_Query.png`

### Final Artifact
- [ ] `final-artifact/jmeter/petclinic_full_scenario.jmx`
- [ ] `CS6075_PRJ1_Experiments_Artifact.zip` generated from `final-artifact/`

---

> **End of Common Setup File.**
> Completing all items above produces sufficient evidence to support every claim in the
> 60% paper draft, plus all raw data (JTLs, CSVs, screenshots) needed for the full
> Results section, Discussion, Threats to Validity, and Conclusion.
>
> Proceed to your assigned experiment files:
> - **Person A (SET X):** `Experiment_1.md` → then `Experiment_2.md`
> - **Person B (SET Y):** `Experiment_3.md` → then `Experiment_4.md`

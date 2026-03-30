# Merge Plan: Unifying All Experiments into `TEMP` Branch

> **Generated**: 2026-03-30  
> **Goal**: Create a single `TEMP` branch containing all four experiments (source code, results, JMX files, screenshots) that can reproduce any experiment without conflicts.  
> **Safety**: The branches `main`, `main-control`, `exp-3-eda`, and `exp-4-saga` are NEVER modified.

---

## 1. Branch Lineage (Source of Truth: Git)

```
main (ece3649) — upstream fork, no experiment work
  └── main-control (2887e1d) — Exp 1 (Baseline Scalability) + Exp 2 (Chaos Resilience)
        │   Commits on top of main:
        │     f529162  common setup
        │     a9b54cf  common setup
        │     64ea5e3  common setup minor fixes
        │     2c12652  Experiment 1 added
        │     2887e1d  Merge pull request #1 (exp-2-chaos merged)
        │
        ├── exp-3-eda (538a7e5) — Exp 3 (EDA Micro-Refactor)
        │     abe3d1e  implement experiment 3 EDA refactoring with rabbitmq and produce results
        │     538a7e5  Improve experiment 3 jmeter flow and regenerate eda comparison
        │
        └── exp-4-saga (a715f30) — Exp 3 base + Exp 4 (Saga Data Consistency)
              abe3d1e  [SAME commit as exp-3 first commit] implement experiment 3 EDA
              804fdc0  Implement experiment 4 saga flow and Jmeter results
              a715f30  Experiment 4 saga validation with results
```

### Key Observation
- `exp-4-saga` shares commit `abe3d1e` with `exp-3-eda` (the initial EDA implementation).
- `exp-4-saga` does NOT contain commit `538a7e5` (the Exp 3 JMX improvement + re-run results).
- `exp-4-saga`'s source code is a **superset** of `exp-3-eda`'s source code (it adds saga-specific code on top of the shared EDA foundation).

---

## 2. File-by-File Conflict Analysis

### 2.1 Source Code Files (Java)

| File | main-control | exp-3-eda | exp-4-saga | Conflict? | Resolution |
|------|-------------|-----------|-----------|-----------|------------|
| `AsyncVisitCommandController.java` | N/A | Full impl, default="JMeter EDA visit" | Same impl, default="JMeter saga visit" | **Trivial** — only the default description string differs | Use exp-4 version (superset). The default string is irrelevant when JMX sends a body. |
| `VisitEventListener.java` | N/A | Basic version (no null checks) | Enhanced version (null-safe, supports both "visitDate" and "date" keys) | **Backward compatible** | Use exp-4 version — it's strictly safer. |
| `Visit.java` | N/A | Has `status` field | Has `status` + `createdAt` fields | **Additive** | Use exp-4 version. `createdAt` is a new column that doesn't break exp-3. |
| `VisitRepository.java` | N/A | Basic (findByPetId, findByPetIdIn) | Adds `countStatusesSince()` + `VisitStatusCount` interface | **Additive** | Use exp-4 version. Extra query doesn't affect exp-3. |
| `VisitIntegrityResource.java` | N/A | N/A | New REST endpoint `/visits/integrity` | **New file** | Include it. It's only invoked explicitly for Exp 4 validation. |
| `VisitCompensationListener.java` | N/A | Same | Same | No conflict | Identical on both branches. |
| `RabbitVisitConfig.java` | N/A | Same | Same | No conflict | Identical on both branches. |
| `RabbitGatewayConfig.java` | N/A | Same | Same | No conflict | Identical on both branches. |
| `ApiGatewayController.java` | N/A | Same | Same | No conflict | Identical on both branches. |
| `ApiGatewayControllerTest.java` | N/A | Same | Same | No conflict | Identical on both branches. |
| `AsyncVisitCommandControllerTest.java` | N/A | Same | Same | No conflict | Identical on both branches. |
| `mock-billing-service/*` | N/A | N/A | New microservice (4 files) | **New module** | Include it. Only used when Exp 4's docker-compose profile runs. |

**Verdict**: exp-4-saga's Java source is a clean **superset** of exp-3-eda. No code conflicts.

### 2.2 Configuration Files

| File | main-control | exp-3-eda | exp-4-saga | Resolution |
|------|-------------|-----------|-----------|------------|
| `api-gateway application.yml` | CircuitBreaker + Resilience4j config, `- Method=GET` route filter | Same as main-control + RabbitMQ config + `- Method=GET` | Same as exp-3-eda (identical) | Use exp-4 version (same as exp-3). |
| `visits-service application.yml` | Baseline (no RabbitMQ) | + RabbitMQ config | Same as exp-3 (identical) | Use exp-4 version (same as exp-3). |
| `api-gateway pom.xml` | Baseline | + spring-boot-starter-amqp | Same as exp-3 (identical) | Use exp-4 version. |
| `visits-service pom.xml` | Baseline | + spring-boot-starter-amqp | Same as exp-3 (identical) | Use exp-4 version. |
| Root `pom.xml` | No mock-billing module | No mock-billing module | + `<module>mock-billing-service</module>` | Use exp-4 version. |
| DB schemas (hsqldb + mysql) | Baseline (no status/created_at) | + `status`, `created_at` columns | Same as exp-3 (identical) | Use exp-4 version (same as exp-3). |
| DB data.sql files | Baseline | + explicit column names with `status='ACTIVE'` | Same as exp-3 (identical) | Use exp-4 version. |

### 2.3 Docker-Compose

| Section | main-control | exp-3-eda | exp-4-saga | Resolution |
|---------|-------------|-----------|-----------|------------|
| `rabbitmq` service | N/A | Added | Same as exp-3 | Include |
| `visits-service` RabbitMQ env | N/A | Added | Same as exp-3 | Include |
| `visits-service` healthcheck port | 9966 (wrong) | 8082 (fixed) | Same as exp-3 | Include (8082 is correct) |
| `api-gateway` RabbitMQ env | N/A | Added | Same as exp-3 | Include |
| `mock-billing-service` | N/A | N/A | Added | Include |

**Impact on Exp 1 & 2**: RabbitMQ and mock-billing-service will start but are harmless — they sit idle. The core services (customers, vets, visits, api-gateway) function identically for baseline/chaos testing. The visits-service still accepts synchronous POST via the existing `VisitResource` controller (the `- Method=GET` filter only affects the gateway route, not the direct service endpoint). The circuit breaker config is preserved.

### 2.4 JMX Files (THE KEY CONFLICT)

Each experiment used different JMeter configurations:

| Parameter | Exp 1&2 (main-control) | Exp 3 (exp-3-eda) | Exp 4 (exp-4-saga) |
|-----------|----------------------|-------------------|-------------------|
| `num_threads` | 1000 | 1000 | 100 |
| `ramp_time` | 30 | 30 | 30 |
| `duration` | 120 | 120 | 0 |
| `scheduler` | true | true | false |
| `loops` | 10000 | 10000 | 1 |
| Content-Type header | No | Yes (JSON) | Yes (JSON) |
| Vet GET body | Empty | JSON body (mistakenly) | Empty |
| Visit POST body | Empty (form) | Empty (let controller use default) | JSON `{"date":"2026-03-28","description":"JMeter saga visit"}` |
| Visit POST endpoint | `/api/visit/...` (sync) | `/api/visit/...` (async via RabbitMQ, gateway routes POST to AsyncVisitCommandController) | `/api/visit/...` (async via RabbitMQ) |

**Resolution**: Create **per-experiment JMX files** in `jmeter/` directory:
- `jmeter/exp1_exp2_baseline_chaos.jmx` — Copy from `main-control` (original Exp 1&2 JMX)
- `jmeter/exp3_eda_refactor.jmx` — Copy from `exp-3-eda` (improved JMX from commit 538a7e5)
- `jmeter/exp4_saga_validation.jmx` — Copy from `exp-4-saga` (Exp 4 JMX)
- `jmeter/petclinic_full_scenario.jmx` — Keep exp-4-saga's version as the "current" default

### 2.5 Results & Screenshots

| Directory/File | Source Branch | Notes |
|---------------|--------------|-------|
| `results/experiment-1/*` | main-control | Unique to Exp 1 |
| `results/experiment-2/*` | main-control | Unique to Exp 2 |
| `results/experiment-3/*` | **exp-3-eda** (NOT exp-4-saga) | exp-3-eda has improved/re-run results (commit 538a7e5). exp-4-saga has stale first-run results. |
| `results/experiment-4/*` | exp-4-saga | Unique to Exp 4 |
| `screenshots/Exp1_*` | main-control | Unique to Exp 1 |
| `screenshots/Exp2_*` | main-control | Unique to Exp 2 |
| `screenshots/Exp3_*` | **exp-3-eda** | exp-3-eda has the final screenshots including `Exp3_RabbitMQ_visit_confirmed.png` which was deleted in exp-4-saga |
| `screenshots/Exp4_*` | exp-4-saga | Unique to Exp 4 |
| `jmeter.log` | main-control | Runtime artifact, not critical but present |

### 2.6 Other Files

| File | Notes |
|------|-------|
| `Playbook/*` | Identical across all branches. No conflict. |
| `scripts/*` | Identical across all branches. No conflict. |
| `.gitignore` | Identical across all branches. No conflict. |

---

## 3. Merge Strategy

### Approach: Start from `exp-4-saga`, layer in exp-3's improved results + per-experiment JMX files

**Why start from exp-4-saga?**
- It has the most complete source code (superset of all experiments)
- It already contains Exp 1&2 (via main-control lineage) + Exp 3 base code + Exp 4 additions
- Only missing: Exp 3's improved results (from commit 538a7e5) and per-experiment JMX separation

### Step-by-Step Commands

```bash
# ============================================================
# STEP 0: Safety check — we are NOT on any protected branch
# ============================================================
git branch   # verify current branch

# ============================================================
# STEP 1: Create TEMP branch from exp-4-saga
# ============================================================
git checkout -b TEMP exp-4-saga

# ============================================================
# STEP 2: Bring in Exp 3's improved results from exp-3-eda
# ============================================================
# Overwrite exp-3 results with the improved/re-run versions from exp-3-eda
git checkout exp-3-eda -- results/experiment-3/

# Bring in the exp-3 screenshot that was deleted in exp-4-saga
git checkout exp-3-eda -- screenshots/Exp3_RabbitMQ_visit_confirmed.png

# Also bring the improved exp-3 screenshots
git checkout exp-3-eda -- screenshots/Exp3_Grafana_1000_users.png
git checkout exp-3-eda -- screenshots/Exp3_RabbitMQ_Dashboard.png

# ============================================================
# STEP 3: Create per-experiment JMX files
# ============================================================
# Copy the JMX from main-control for Exp 1 & 2
git show main-control:jmeter/petclinic_full_scenario.jmx > jmeter/exp1_exp2_baseline_chaos.jmx

# Copy the improved JMX from exp-3-eda for Exp 3
git show exp-3-eda:jmeter/petclinic_full_scenario.jmx > jmeter/exp3_eda_refactor.jmx

# Copy the JMX from exp-4-saga for Exp 4
git show exp-4-saga:jmeter/petclinic_full_scenario.jmx > jmeter/exp4_saga_validation.jmx

# Keep the current petclinic_full_scenario.jmx as-is (exp-4-saga version, already in tree)

# ============================================================
# STEP 4: Commit
# ============================================================
git add -A
git commit -m "Unify all experiments: per-experiment JMX files, exp-3 improved results, all code"
```

### What This Produces

The `TEMP` branch will contain:

```
jmeter/
  petclinic_full_scenario.jmx        ← exp-4-saga version (current default)
  exp1_exp2_baseline_chaos.jmx       ← original Exp 1&2 JMX
  exp3_eda_refactor.jmx              ← improved Exp 3 JMX (from 538a7e5)
  exp4_saga_validation.jmx           ← Exp 4 JMX

results/
  experiment-1/                       ← from main-control (already in tree)
  experiment-2/                       ← from main-control (already in tree)
  experiment-3/                       ← from exp-3-eda (improved, replaces stale version)
  experiment-4/                       ← from exp-4-saga (already in tree)

screenshots/
  Exp1_*                              ← from main-control (already in tree)
  Exp2_*                              ← from main-control (already in tree)
  Exp3_*                              ← from exp-3-eda (improved + restored deleted file)
  Exp4_*                              ← from exp-4-saga (already in tree)

Source code:                          ← exp-4-saga version (superset of all experiments)
  - mock-billing-service/             (Exp 4 only)
  - RabbitMQ/EDA classes              (Exp 3 & 4)
  - Visit model with status+createdAt (Exp 3 & 4, backward compatible)
  - VisitIntegrityResource            (Exp 4 only)
  - CircuitBreaker config             (Exp 2)
  - Chaos Monkey dep                  (Exp 2, already in upstream)
```

---

## 4. Reproducibility Guide: Running Each Experiment

### Experiment 1 — Baseline Scalability
```bash
# Uses: jmeter/exp1_exp2_baseline_chaos.jmx
# Services: docker-compose up (all services start; RabbitMQ/mock-billing idle but harmless)
# Note: Thread count configured in JMX (100/500/1000). Modify num_threads before each run.
# The JMX uses synchronous visit POST (no JSON body → form data → hits VisitResource directly)
docker-compose up -d --build
jmeter -n -t jmeter/exp1_exp2_baseline_chaos.jmx -l results/experiment-1/run.jtl
```

### Experiment 2 — Chaos Resilience
```bash
# Uses: jmeter/exp1_exp2_baseline_chaos.jmx (same as Exp 1)
# Services: docker-compose up
# Additional: Enable Chaos Monkey via scripts/chaos/
# CircuitBreaker config is in api-gateway application.yml (resilience4j section)
docker-compose up -d --build
# Enable chaos attacks (see Playbook/Experiment_2_Chaos_Resilience.md)
./scripts/chaos/call_chaos.sh
jmeter -n -t jmeter/exp1_exp2_baseline_chaos.jmx -l results/experiment-2/run.jtl
```

### Experiment 3 — EDA Micro-Refactor
```bash
# Uses: jmeter/exp3_eda_refactor.jmx
# Services: docker-compose up (RabbitMQ is used; mock-billing-service is idle)
# Key: api-gateway routes POST /api/visit/** to AsyncVisitCommandController (via RabbitMQ)
#      GET requests to visits-service are routed normally (- Method=GET filter)
# Thread count configured in JMX (modify num_threads for 100/500/1000)
docker-compose up -d --build
jmeter -n -t jmeter/exp3_eda_refactor.jmx -l results/experiment-3/run.jtl
```

### Experiment 4 — Saga Data Consistency
```bash
# Uses: jmeter/exp4_saga_validation.jmx
# Services: docker-compose up (ALL services including mock-billing-service)
# mock-billing-service listens on RabbitMQ and can trigger compensation flows
# The JMX sends JSON body with saga visit creation
# After JMeter run, validate with: curl http://localhost:8082/visits/integrity?minutes=10
docker-compose up -d --build
jmeter -n -t jmeter/exp4_saga_validation.jmx -l results/experiment-4/run.jtl
curl http://localhost:8082/visits/integrity?minutes=10
```

---

## 5. Why This Works Without Breaking Anything

1. **Exp 1 & 2 (Baseline/Chaos)**: Their JMX sends visits via synchronous POST without a JSON body. The `- Method=GET` gateway filter means GET requests go to visits-service normally. POST requests go to `AsyncVisitCommandController` which accepts form data and uses `defaultVisit()`. The visits still get created (via RabbitMQ now, asynchronously). The extra RabbitMQ/mock-billing containers start but don't interfere with the test — they just handle the async visit persistence. The chaos monkey configuration and circuit breaker settings are preserved in the same `application.yml`.

2. **Exp 3 (EDA)**: Uses the improved JMX with proper Content-Type headers. The source code is the same superset — the extra `createdAt` field and `VisitIntegrityResource` are additive and don't change EDA behavior.

3. **Exp 4 (Saga)**: Uses its own JMX with 100 threads, 1 loop, and saga-specific JSON body. The `mock-billing-service` subscribes to RabbitMQ and can trigger compensations. The `VisitIntegrityResource` endpoint validates data consistency.

---

## 6. Files That Differ Between exp-3-eda and exp-4-saga (Resolved)

| File | exp-3-eda (538a7e5) | exp-4-saga (a715f30) | TEMP branch | Reason |
|------|--------------------|--------------------|-------------|--------|
| `AsyncVisitCommandController.java` | default="JMeter EDA visit" | default="JMeter saga visit" | exp-4-saga | Default text is irrelevant; JMX sends explicit body. Both parse identically. |
| `VisitEventListener.java` | No null checks | Null-safe parsing, supports "date" key alias | exp-4-saga | Strictly safer; backward compatible. |
| `Visit.java` | status only | status + createdAt | exp-4-saga | Additive field; doesn't break exp-3 flows. |
| `VisitRepository.java` | Basic queries | + countStatusesSince() | exp-4-saga | Additive query; only invoked by VisitIntegrityResource. |
| `docker-compose.yml` | No mock-billing | + mock-billing-service | exp-4-saga | Idle service doesn't break other experiments. |
| `pom.xml` (root) | No mock-billing module | + mock-billing-service module | exp-4-saga | Needed for build; doesn't affect runtime of other experiments. |
| `results/experiment-3/*` | Improved/re-run data | Stale first-run data | **exp-3-eda** | exp-3-eda has the correct, improved results. |
| `screenshots/Exp3_*` | Updated screenshots | Different screenshots | **exp-3-eda** | exp-3-eda has the authoritative exp-3 screenshots. |
| `jmeter/petclinic_full_scenario.jmx` | Exp 3 config | Exp 4 config | exp-4-saga + per-experiment copies | Solved by per-experiment JMX files. |

---

## 7. Verification Checklist (Post-Merge)

After creating the `TEMP` branch, verify:

- [ ] `git log --oneline TEMP` shows exp-4-saga lineage + 1 unification commit
- [ ] `ls jmeter/` shows 4 JMX files (1 default + 3 per-experiment)
- [ ] `ls results/` shows experiment-1, experiment-2, experiment-3, experiment-4
- [ ] `results/experiment-3/Exp3_EDA_vs_Baseline.csv` matches exp-3-eda version (100 users: p99=99ms)
- [ ] `screenshots/Exp3_RabbitMQ_visit_confirmed.png` exists (restored from exp-3-eda)
- [ ] `mock-billing-service/` directory exists with 4 source files
- [ ] `spring-petclinic-visits-service/.../VisitIntegrityResource.java` exists
- [ ] Root `pom.xml` includes `<module>mock-billing-service</module>`
- [ ] `docker-compose.yml` has: rabbitmq, mock-billing-service, visits-service with RabbitMQ env, api-gateway with RabbitMQ env
- [ ] `spring-petclinic-api-gateway/.../application.yml` has resilience4j circuit breaker config AND `- Method=GET` filter AND RabbitMQ config

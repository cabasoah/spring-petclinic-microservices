# Experiment 1 — Baseline Scalability Configuration

## JMeter Test Plan
- **File**: `exp1_exp2_baseline_chaos.jmx` (shared with Exp 2)
- **Thread counts tested**: 100, 500, 1000 users
- **Ramp-up**: 30 seconds
- **Duration**: 120 seconds (scheduler enabled)
- **Loops**: 10000
- **Endpoints hit**:
  - `GET /api/vet/vets` — Veterinarians page
  - `GET /api/gateway/owners/1` — Single owner lookup
  - `POST /api/visit/owners/1/pets/1/visits` — Create visit (synchronous, no JSON body)
  - `GET /api/gateway/owners/1` — Owner lookup again

## Service Ports
| Service | Port |
|---------|------|
| Config Server | 8888 |
| Discovery Server (Eureka) | 8761 |
| API Gateway | 8080 |
| Customers Service | 8081 |
| Visits Service | 8082 |
| Vets Service | 8083 |
| Grafana | 3030 |
| Prometheus | 9091 |
| Zipkin (Tracing) | 9411 |
| Admin Server | 9090 |

## Key Config
- No RabbitMQ needed (visits are synchronous)
- No Chaos Monkey enabled
- Docker Compose: standard `docker-compose up -d --build`
- Visit creation goes through API Gateway → visits-service `VisitResource` (synchronous JPA save)

## Results Summary
| Users | Avg (ms) | p99 (ms) | Throughput (req/s) | Error % |
|-------|----------|----------|--------------------|---------|
| 100 | See `Exp1_Summary_100.csv` | 375 | 684.20 | See CSV |
| 500 | See `Exp1_Summary_500.csv` | 691 | 1633.37 | See CSV |
| 1000 | See `Exp1_Summary_1000.csv` | 1875 | 1356.86 | See CSV |

## Files in This Folder
- `exp1_exp2_baseline_chaos.jmx` — JMeter test plan
- `Exp1_Baseline_Summary_Table.csv` — Aggregated summary
- `Exp1_Summary_100.csv`, `Exp1_Summary_500.csv`, `Exp1_Summary_1000.csv` — Per-load-level summaries
- `Exp1_JMeter_100.jtl`, `Exp1_JMeter_500.jtl`, `Exp1_JMeter_1000.jtl` — Raw JMeter results
- `Exp1_Grafana_*.png` — Grafana dashboard screenshots per load level
- `Exp1_JMeter_Plan.png` — JMeter test plan screenshot

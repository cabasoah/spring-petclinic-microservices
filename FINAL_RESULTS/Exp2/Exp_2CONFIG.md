# Experiment 2 — Chaos Resilience Configuration

## JMeter Test Plan
- **File**: `exp1_exp2_baseline_chaos.jmx` (shared with Exp 1)
- **Thread count**: 1000 users
- **Ramp-up**: 30 seconds
- **Duration**: 120 seconds (scheduler enabled)
- **Loops**: 10000
- **Same endpoints as Exp 1**

## Chaos Monkey Configuration
Chaos Monkey for Spring Boot was enabled on the `visits-service` during the test run.

### Watcher (enabled on visits-service)
```json
// scripts/chaos/watcher_enable_restcontroller.json
{
  "controller": false,
  "restController": true,
  "service": false,
  "repository": false,
  "component": false
}
```

### Attack: Kill Application
```json
// scripts/chaos/attacks_enable_killapplication.json
{
  "level": 1,
  "killApplicationActive": true,
  "latencyActive": false,
  "exceptionsActive": false,
  "memoryActive": false
}
```

The visits-service was killed mid-test to trigger the circuit breaker.

## Circuit Breaker Configuration (api-gateway `application.yml`)
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

### Gateway Route Filter
```yaml
- id: visits-service
  uri: lb://visits-service
  predicates:
    - Path=/api/visit/**
  filters:
    - StripPrefix=2
    - CircuitBreaker=name=visits,fallbackUri=forward:/fallback
```

## Observed Circuit Breaker State Transitions
1. **CLOSED** → Normal operation, requests pass through
2. **OPEN** → After visits-service killed, failure rate exceeded 50% threshold
3. **HALF_OPEN** → After `waitDurationInOpenState` (10s), 3 probe calls permitted
4. **CLOSED** → visits-service restarted (`restart: on-failure`), probes succeed

## Service Ports
Same as Experiment 1 (see `../Exp1/CONFIG.md`).

## Files in This Folder
- `exp1_exp2_baseline_chaos.jmx` — JMeter test plan
- `Exp2_Chaos_Summary.csv` — Chaos test summary
- `Exp2_CircuitBreaker_States.log` — Circuit breaker state transition log
- `Exp2_JMeter_Chaos.jtl` — Raw JMeter results during chaos
- `Exp2_CircuitBreaker_States.png` — Screenshot of circuit breaker state transitions
- `Exp2_Grafana_Outage.png` — Grafana dashboard during outage

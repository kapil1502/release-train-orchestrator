# release-train-orchestrator

# 🛠️ Architecture & Tech Stack Decisions

This project is built as an **event-driven Control Plane** that automates progressive canary rollouts (e.g., 1% → 5% → 25% → 100%) across distributed microservices. Every component in this stack was selected to guarantee strict state ordering, isolate deployment failures, and enable declarative, GitOps-friendly release workflows.

## Core Tech Stack

| Layer | Technology | Role in Architecture |
| :--- | :--- | :--- |
| **Core Orchestrator** | **Java 17 (LTS) + Spring Boot** | Drives the rollout state machine, exposes developer deployment APIs, and manages automated stage progression and rollbacks. |
| **Event Streaming Bus** | **Apache Kafka (`spring-kafka`)** | Decouples rollout commands from downstream execution; ingests real-time lifecycle events (`DeployStarted`, `HealthCheckPassed`, `DeployFailed`). |
| **Persistent State Ledger** | **Spring Data JPA + PostgreSQL / H2** | Stores ACID-compliant rollout state transitions, step audit trails, and active deployment configurations for crash recovery. |
| **Configuration Engine** | **Jackson Dataformat YAML** | Deserializes declarative `deployment-spec.yaml` contracts into immutable Java 17 `record` models at runtime. |
| **Downstream Simulator** | **Spring Boot Worker Service** | Consumes rollout stage commands from Kafka and emits realistic deployment and health-check telemetry events. |

---

## Why This Stack? (Key Engineering Trade-offs)

### 1. Apache Kafka over Synchronous HTTP Polling or Simple Queues
- **The Bottleneck:** Polling downstream services synchronously over HTTP during multi-stage rollouts creates tight coupling, risks thread starvation during latency spikes, and loses event history if a consumer restarts.
- **The Decision:** Downstream microservices emit lifecycle events asynchronously into **Kafka** topics partitioned by `deploymentId`. Partitioning guarantees strict sequential ordering per release train while retaining an immutable event log for crash recovery and audit replay.

### 2. Java 17 (LTS) + Spring Boot over Lightweight Scripting Runtimes
- **The Bottleneck:** Managing concurrent state machines, scheduled rollout pauses, and transactional database + Kafka commits in dynamically typed runtimes introduces race conditions and fragile error handling.
- **The Decision:** **Java 17** provides immutable `record` classes for clean Kafka event schemas, `sealed` interfaces for strict state-machine modeling, and long-term enterprise stability, paired with Spring Boot's mature `@KafkaListener` concurrency controls and transactional guarantees.

### 3. Persistent State Machine + Relational Ledger over In-Memory State
- **The Bottleneck:** Holding active rollout stages solely in application memory means a process restart or container crash leaves half-deployed services orphaned mid-rollout.
- **The Decision:** Every step transition (`CREATED` → `STEP_IN_PROGRESS` → `HEALTH_CHECKING` → `ROLLBACK_IN_PROGRESS`) is persisted via **Spring Data JPA**. If the orchestrator restarts mid-train, it resumes from the last committed state without duplicating or skipping traffic increments.

### 4. Declarative YAML Contracts over Hardcoded Pipeline Logic
- **The Bottleneck:** Hardcoding rollout percentages, wait windows, and target services inside backend code requires redeploying the orchestrator whenever a team tweaks its release strategy.
- **The Decision:** Developers submit self-contained `deployment-spec.yaml` files via REST API. **Jackson YAML** parses these contracts dynamically into execution plans, decoupling platform orchestration logic from individual service release schedules.

---

## 🔧 Known Limitations & Future Improvements

### Concurrent Updates to Deployment State (Optimistic Locking)
- **Why It Matters:** Kafka topics are partitioned by `deploymentId`, so in normal operation only one consumer processes a given rollout at a time — including during graceful rolling deploys and autoscaling, where rebalances hand partitions over cleanly between records. Partitioning alone does **not** protect against:
  - **Zombie consumers:** a consumer that exceeds `max.poll.interval.ms` (default 5 min, e.g. slow batch of up to `max.poll.records` = 500) is evicted from the group, but its thread keeps running and can still write to Postgres while the new partition owner processes the same deployment.
  - **Multiple writers outside Kafka:** the Phase 4 REST API (e.g. a manual rollback) or a step scheduler updating the same deployment as a Kafka consumer.
  - **Repartitioning a live topic:** increasing the partition count remaps keys (`hash(key) % partitions`), so in-flight events for one deployment can land on two partitions. Avoid by provisioning partitions generously up front, or migrating to a new topic.
- **Implemented:** `DeploymentEntity` carries a `@Version` column (`rowVersion`). Every transition issues `UPDATE ... WHERE row_version = ?`, so a write based on a stale read fails with `ObjectOptimisticLockingFailureException` instead of silently overwriting a newer status (e.g. a `ROLLBACK_IN_PROGRESS` being replaced by `STEP_COMPLETED`).
- **Still To Do:**
  - **Conflict handling in callers:** Kafka listeners should retry on `ObjectOptimisticLockingFailureException` (re-read and re-validate the transition); REST endpoints should return `409 Conflict`.
  - **Idempotent event handling:** Kafka delivers at least once — a crash after the DB commit but before the offset commit redelivers the event. Events should carry the step index they belong to and be ignored if it does not match `currentStepIndex`, so a duplicate `AdvanceToNextStep` cannot skip a traffic increment.
  - **Consumer tuning:** lower `max.poll.records` and keep listeners free of slow calls to reduce the chance of zombie consumers.

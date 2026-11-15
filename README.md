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
- **Current Behavior:** `RolloutStateMachineService.transition()` reads a `DeploymentEntity`, validates the transition, and saves the new status inside a single `@Transactional` boundary. `DeploymentEntity` has no `@Version` column, so the database does not detect concurrent writers.
- **Why It Works Today:** Kafka topics are partitioned by `deploymentId`, so only one consumer thread processes events for a given rollout at a time. Transitions for a single deployment are therefore applied sequentially.
- **The Risk:** If a second writer is introduced — e.g. a REST endpoint for a manual rollback or pause, a scheduler that advances steps after `pauseDurationSeconds`, or a consumer group rebalance that briefly overlaps two consumers — two transactions can read the same status, both pass validation, and the last commit wins. This is a classic *lost update* that can leave a rollout in a state the state machine never allowed (e.g. a manual `ROLLBACK_IN_PROGRESS` silently overwritten by `STEP_COMPLETED`).
- **Proposed Improvement:** Add a `@Version private long version;` field to `DeploymentEntity` so Hibernate issues `UPDATE ... WHERE version = ?`. A stale write then fails with `ObjectOptimisticLockingFailureException`, which callers should handle by re-reading the entity and re-validating the transition (retry for Kafka consumers, `409 Conflict` for REST callers). Pessimistic locking (`@Lock(PESSIMISTIC_WRITE)` on the repository lookup) is an alternative if contention turns out to be high.

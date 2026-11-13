# Project: Release Train Orchestrator

## Tech Stack
- **Language:** Java 17 (LTS)
- **Framework:** Spring Boot 3.4 (Web, Data JPA, Apache Kafka)
- **Serialization:** Jackson (`jackson-dataformat-yaml`)
- **Database:** PostgreSQL (prod/docker), H2 (unit tests)
- **Base Package:** `com.developer.kapil.orchestrator`

## Essential Commands
- Build & run unit tests: `./mvnw test`
- Full build (skip tests): `./mvnw clean package -DskipTests`
- Start local infra (Kafka + Postgres): `docker compose up -d`

## Coding Conventions
- Use Java 17 `record` types for immutable DTOs, YAML config models, and Kafka event payloads.
- Keep state machine transitions explicit and persisted via Spring Data JPA before emitting downstream Kafka commands.
- Partition Kafka topics by `deploymentId` to guarantee strict event ordering per rollout.
- Every service class must have a corresponding JUnit 5 + Mockito test in `src/test/java`.

## Reference Docs
- System design & Kafka event contracts: `@docs/ARCHITECTURE.md`
- Implementation phases: `@docs/roadmap/`
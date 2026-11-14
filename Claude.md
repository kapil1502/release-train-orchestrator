# Project: Release Train Orchestrator

## Tech Stack
- **Language:** Java 17 (LTS)
- **Framework:** Spring Boot 4.1 (Web MVC, Data JPA, Apache Kafka)
- **Serialization:** Jackson 3 (`tools.jackson.*` packages, `tools.jackson.dataformat:jackson-dataformat-yaml`) — do not use Jackson 2 `com.fasterxml.jackson.databind` imports
- **Database:** PostgreSQL (prod/docker), H2 (unit tests)
- **Base Package:** `io.github.kapil1502.releasetrain`

## Essential Commands
- Build & run unit tests: `./mvnw test`
- Full build (skip tests): `./mvnw clean package -DskipTests`
- Start local infra (Kafka + Postgres): `docker compose up -d`

## Coding Conventions
- Use Java 17 `record` types for immutable DTOs, YAML config models, and Kafka event payloads.
- Keep state machine transitions explicit and persisted via Spring Data JPA before emitting downstream Kafka commands.
- Partition Kafka topics by `deploymentId` to guarantee strict event ordering per rollout.
- Every service class must have a corresponding JUnit 5 + Mockito test in `src/test/java`.

## Git Commit Conventions for Claude Code

When executing commits or generating commit commands, follow the **Conventional Commits** specification.

### Format
```text
<type>(<scope>): <short description>
```

### Allowed Types
- `feat`: A new feature or capability for the application.
- `fix`: A bug fix.
- `docs`: Documentation-only changes (README, architecture, roadmaps).
- `refactor`: Code changes that neither fix a bug nor add a feature.
- `test`: Adding missing tests or correcting existing ones.
- `chore`: Build processes, dependency updates, scaffolding, or tool configs.

### Rules
1. **Imperative Mood:** Use "add" instead of "added" or "adding" (e.g., `feat(api): add deployment endpoint`).
2. **Lowercase Description:** Keep the description in lowercase.
3. **Scope Required:** Always specify the component scope in parentheses (e.g., `(kafka)`, `(state)`, `(parser)`, `(init)`).

### Reference Docs
- System design & Kafka event contracts: `@docs/ARCHITECTURE.md`
- Implementation phases: `@docs/roadmap/`
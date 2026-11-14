# Roadmap Phase 4: REST API Controller & Local Infrastructure

## 1. Objective

Expose the developer-facing REST API (`POST /api/v1/deployments`) that accepts raw YAML specifications (`application/x-yaml`), initializes the deployment entity, triggers the state machine, and dispatches the initial Kafka command. Additionally, provide the local infrastructure configuration via `docker-compose.yml`.

## 2. Target File Structure

Claude Code must create or modify the following files during this phase:

```
src/
├── main/
│   │   └── io/github/kapil1502/releasetrain/
│   │       ├── controller/
│   │       ├──  DeploymentController.java
│   │       └── service/
│   │           └── DeploymentOrchestrationService.java
│   └── resources/
│       └── application.yml
└── docker-compose.yml
```

## 3. Implementation Steps for Claude Code

### Step 1: Implement Orchestration Service (`service/`)
Create a service that coordinates parsing the YAML, persisting the initial database entity, and dispatching the first rollout command via Kafka.

* **`io.github.kapil1502.releasetrain.service.DeploymentOrchestrationService.java`**
  ```java
  package io.github.kapil1502.releasetrain.service;

  import io.github.kapil1502.releasetrain.domain.DeploymentEntity;
  import io.github.kapil1502.releasetrain.domain.DeploymentStatus;
  import io.github.kapil1502.releasetrain.dto.DeploymentSpec;
  import io.github.kapil1502.releasetrain.dto.RolloutCommand;
  import io.github.kapil1502.releasetrain.messaging.CommandProducer;
  import io.github.kapil1502.releasetrain.repository.DeploymentRepository;
  import org.springframework.stereotype.Service;
  import org.springframework.transaction.annotation.Transactional;

  import java.io.InputStream;
  import java.time.Instant;
  import java.util.UUID;

  @Service
  public class DeploymentOrchestrationService {

      private final DeploymentConfigParser parser;
      private final DeploymentRepository deploymentRepository;
      private final RolloutStateMachineService stateMachineService;
      private final CommandProducer commandProducer;

      public DeploymentOrchestrationService(
              DeploymentConfigParser parser,
              DeploymentRepository deploymentRepository,
              RolloutStateMachineService stateMachineService,
              CommandProducer commandProducer) {
          this.parser = parser;
          this.deploymentRepository = deploymentRepository;
          this.stateMachineService = stateMachineService;
          this.commandProducer = commandProducer;
      }

      @Transactional
      public DeploymentEntity submitDeployment(InputStream yamlInputStream) {
          DeploymentSpec spec = parser.parse(yamlInputStream);

          // 1. Create initial entity
          DeploymentEntity entity = new DeploymentEntity();
          entity.setDeploymentId(spec.deploymentId());
          entity.setServiceName(spec.serviceName());
          entity.setVersion(spec.version());
          entity.setStatus(DeploymentStatus.CREATED);
          entity.setCurrentStepIndex(0);
          deploymentRepository.save(entity);

          // 2. Transition to STEP_IN_PROGRESS (Step 1)
          DeploymentEntity updated = stateMachineService.transition(spec.deploymentId(), DeploymentStatus.STEP_IN_PROGRESS);

          // 3. Dispatch command after commit
          var firstStep = spec.strategy().steps().get(0);
          RolloutCommand command = new RolloutCommand(
                  UUID.randomUUID().toString(),
                  spec.deploymentId(),
                  spec.serviceName(),
                  spec.version(),
                  "EXECUTE_STEP",
                  firstStep.step(),
                  firstStep.percentage(),
                  Instant.now()
          );
          commandProducer.sendCommandAfterCommit(command);

          return updated;
      }
  }
  ```

### Step 2: Implement REST Controller (`controller/`)
Expose the endpoint accepting `application/x-yaml`.

* **`io.github.kapil1502.releasetrain.controller.DeploymentController.java`**
  ```java
  package io.github.kapil1502.releasetrain.controller;

  import io.github.kapil1502.releasetrain.domain.DeploymentEntity;
  import io.github.kapil1502.releasetrain.repository.DeploymentRepository;
  import io.github.kapil1502.releasetrain.service.DeploymentOrchestrationService;
  import org.springframework.http.ResponseEntity;
  import org.springframework.web.bind.annotation.*;

  import java.io.InputStream;

  @RestController
  @RequestMapping("/api/v1/deployments")
  public class DeploymentController {

      private final DeploymentOrchestrationService orchestrationService;
      private final DeploymentRepository deploymentRepository;

      public DeploymentController(
              DeploymentOrchestrationService orchestrationService,
              DeploymentRepository deploymentRepository) {
          this.orchestrationService = orchestrationService;
          this.deploymentRepository = deploymentRepository;
      }

      @PostMapping(consumes = {"application/x-yaml", "text/yaml"})
      public ResponseEntity<DeploymentEntity> createDeployment(InputStream body) {
          DeploymentEntity entity = orchestrationService.submitDeployment(body);
          return ResponseEntity.accepted().body(entity);
      }

      @GetMapping("/{id}")
      public ResponseEntity<DeploymentEntity> getDeploymentStatus(@PathVariable String id) {
          return deploymentRepository.findById(id)
                  .map(ResponseEntity::ok)
                  .orElse(ResponseEntity.notFound().build());
      }
  }
  ```

### Step 3: Create Local Infrastructure (`docker-compose.yml`)
* **`docker-compose.yml`**
  ```yaml
  version: '3.8'

  services:
    postgres:
      image: postgres:15-alpine
      container_name: release_postgres
      environment:
        POSTGRES_DB: orchestrator
        POSTGRES_USER: postgres
        POSTGRES_PASSWORD: password
      ports:
        - "5432:5432"
      volumes:
        - pgdata:/var/lib/postgresql/data

    kafka:
      image: apache/kafka:3.7.0
      container_name: release_kafka
      ports:
        - "9092:9092"
      environment:
        KAFKA_NODE_ID: 1
        KAFKA_PROCESS_ROLES: 'broker,controller'
        KAFKA_LISTENERS: 'PLAINTEXT://0.0.0.0:9092,CONTROLLER://0.0.0.0:9093'
        KAFKA_ADVERTISED_LISTENERS: 'PLAINTEXT://localhost:9092'
        KAFKA_CONTROLLER_LISTENER_NAMES: 'CONTROLLER'
        KAFKA_LISTENER_SECURITY_PROTOCOL_MAP: 'CONTROLLER:PLAINTEXT,PLAINTEXT:PLAINTEXT'
        KAFKA_CONTROLLER_QUORUM_VOTERS: '1@localhost:9093'
        KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR: 1
        KAFKA_TRANSACTION_STATE_LOG_MIN_ISR: 1
        KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR: 1
        KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS: 0

  volumes:
    pgdata:
  ```

## 4. Verification & Testing Criteria
Use `curl` to test YAML ingestion:
```bash
curl -X POST http://localhost:8080/api/v1/deployments \
  -H "Content-Type: application/x-yaml" \
  --data-binary "@deployment-spec.yaml"
```

## 5. Acceptance Command
Run `./mvnw spring-boot:run` alongside `docker compose up -d` to verify full local startup and API availability.
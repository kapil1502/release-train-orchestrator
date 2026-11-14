# Roadmap Phase 3: Spring Kafka Event-Driven Messaging & Transaction Safety

## 1. Objective

Implement asynchronous event-driven communication using Apache Kafka and Spring Kafka. Ensure strict transaction safety so that Kafka commands are only dispatched *after* the JPA database transaction commits successfully.

## 2. Target File Structure

Claude Code must create or modify the following files during this phase:

```
src/
├── main/
│   └── java/
│       └── io/github/kapil1502/releasetrain/
│           ├── config/
│           │   └── KafkaTopicConfig.java
│           ├── dto/
│           │   ├── RolloutCommand.java
│           │   └── DeploymentEvent.java
│           └── messaging/
│               ├── CommandProducer.java
│               └── DeploymentEventConsumer.java
└── test/
    └── java/
        └── io/github/kapil1502/releasetrain/
            └── messaging/
                └── KafkaEventFlowTest.java
```

## 3. Implementation Steps for Claude Code

### Step 1: Create Kafka Payload Records (`dto/`)

* **`io.github.kapil1502.releasetrain.dto.RolloutCommand.java`**
  ```package io.github.kapil1502.releasetrain.dto;

  import java.time.Instant;

  public record RolloutCommand(
      String commandId,
      String deploymentId,
      String serviceName,
      String version,
      String commandType, // EXECUTE_STEP, ROLLBACK
      int stepNumber,
      int targetPercentage,
      Instant timestamp
  ) {}
  ```

* **`io.github.kapil1502.releasetrain.dto.DeploymentEvent.java`**
  ```package io.github.kapil1502.releasetrain.dto;

  import java.time.Instant;

  public record DeploymentEvent(
      String eventId,
      String deploymentId,
      String serviceName,
      int stepNumber,
      String eventType, // DeployStarted, HealthCheckPassed, DeployFailed, RollbackCompleted
      String details,
      Instant timestamp
  ) {}
  ```

### Step 2: Configure Kafka Topics (`config/`)

* **`io.github.kapil1502.releasetrain.config.KafkaTopicConfig.java`**
  ```package io.github.kapil1502.releasetrain.config;

  import org.apache.kafka.clients.admin.NewTopic;
  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.kafka.config.TopicBuilder;

  @Configuration
  public class KafkaTopicConfig {

      public static final String TOPIC_COMMANDS = "deployment-commands";
      public static final String TOPIC_EVENTS = "deployment-events";

      @Bean
      public NewTopic deploymentCommandsTopic() {
          return TopicBuilder.name(TOPIC_COMMANDS)
                  .partitions(3)
                  .replicas(1)
                  .build();
      }

      @Bean
      public NewTopic deploymentEventsTopic() {
          return TopicBuilder.name(TOPIC_EVENTS)
                  .partitions(3)
                  .replicas(1)
                  .build();
      }
  }
  ```

### Step 3: Implement Command Producer with After-Commit Hook (`messaging/`)

Per `CLAUDE.md` guidelines, never dispatch Kafka commands before the DB transaction commits.

* **`io.github.kapil1502.releasetrain.messaging.CommandProducer.java`**
  ```package io.github.kapil1502.releasetrain.messaging;

  import io.github.kapil1502.releasetrain.config.KafkaTopicConfig;
  import io.github.kapil1502.releasetrain.dto.RolloutCommand;
  import org.springframework.kafka.core.KafkaTemplate;
  import org.springframework.stereotype.Component;
  import org.springframework.transaction.support.TransactionSynchronization;
  import org.springframework.transaction.support.TransactionSynchronizationManager;

  @Component
  public class CommandProducer {

      private final KafkaTemplate<String, Object> kafkaTemplate;

      public CommandProducer(KafkaTemplate<String, Object> kafkaTemplate) {
          this.kafkaTemplate = kafkaTemplate;
      }

      public void sendCommandAfterCommit(RolloutCommand command) {
          if (TransactionSynchronizationManager.isSynchronizationActive()) {
              TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                  @Override
                  public void afterCommit() {
                      kafkaTemplate.send(KafkaTopicConfig.TOPIC_COMMANDS, command.deploymentId(), command);
                  }
              });
          } else {
              // Fallback if not in an active transaction
              kafkaTemplate.send(KafkaTopicConfig.TOPIC_COMMANDS, command.deploymentId(), command);
          }
      }
  }
  ```

### Step 4: Implement Event Consumer (`messaging/`)

* **`io.github.kapil1502.releasetrain.messaging.DeploymentEventConsumer.java`**
  ```package io.github.kapil1502.releasetrain.messaging;

  import io.github.kapil1502.releasetrain.config.KafkaTopicConfig;
  import io.github.kapil1502.releasetrain.domain.DeploymentStatus;
  import io.github.kapil1502.releasetrain.dto.DeploymentEvent;
  import io.github.kapil1502.releasetrain.service.RolloutStateMachineService;
  import org.springframework.kafka.annotation.KafkaListener;
  import org.springframework.messaging.handler.annotation.Payload;
  import org.springframework.stereotype.Component;

  @Component
  public class DeploymentEventConsumer {

      private final RolloutStateMachineService stateMachineService;

      public DeploymentEventConsumer(RolloutStateMachineService stateMachineService) {
          this.stateMachineService = stateMachineService;
      }

      @KafkaListener(topics = KafkaTopicConfig.TOPIC_EVENTS, groupId = "orchestrator-group")
      public void handleEvent(@Payload DeploymentEvent event) {
          DeploymentStatus targetStatus = switch (event.eventType()) {
              case "DeployStarted" -> DeploymentStatus.HEALTH_CHECKING;
              case "HealthCheckPassed" -> DeploymentStatus.STEP_COMPLETED;
              case "DeployFailed" -> DeploymentStatus.ROLLBACK_IN_PROGRESS;
              case "RollbackCompleted" -> DeploymentStatus.FAILED;
              default -> throw new IllegalArgumentException("Unknown event type: " + event.eventType());
          };

          stateMachineService.transition(event.deploymentId(), targetStatus);
      }
  }
  ```

## 4. Verification & Testing Criteria

Claude Code must write an integration test using `@EmbeddedKafka` to verify event consumption triggers state transitions correctly.

## 5. Acceptance Command
Run `./mvnw test` to verify Kafka producers and consumers.
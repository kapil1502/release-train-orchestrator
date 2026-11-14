# Roadmap Phase 2: Persistent Ledger & State Machine Engine

## 1. Objective
Implement the relational data persistence layer using Spring Data JPA and establish the deterministic finite state machine service that governs deployment lifecycle transitions under `io.github.kapil1502.releasetrain`.

---

## 2. Target File Structure
Claude Code must create or modify the following files during this phase:

```text
src/
├── main/
│   └── java/
│       └── io/github/kapil1502/releasetrain/
│           ├── domain/
│           │   ├── DeploymentEntity.java
│           │   └── DeploymentStatus.java
│           ├── repository/
│           │   └── DeploymentRepository.java
│           └── service/
│               └── RolloutStateMachineService.java
└── test/
    └── java/
        └── io/github/kapil1502/releasetrain/
            └── service/
                └── RolloutStateMachineServiceTest.java
```

---

## 3. Implementation Steps for Claude Code

### Step 1: Create Deployment Status Enum (`domain/`)
Define the finite states matching the Mermaid state diagram in `ARCHITECTURE.md`.

* **`io.github.kapil1502.releasetrain.domain.DeploymentStatus.java`**
  ```java
  package io.github.kapil1502.releasetrain.domain;

  public enum DeploymentStatus {
      CREATED,
      STEP_IN_PROGRESS,
      HEALTH_CHECKING,
      STEP_COMPLETED,
      ROLLBACK_IN_PROGRESS,
      SUCCESSFUL,
      FAILED
  }
  ```

### Step 2: Create JPA Entity Model (`domain/`)
Create the persistent entity mapped to PostgreSQL.

* **`io.github.kapil1502.releasetrain.domain.DeploymentEntity.java`**
  ```java
  package io.github.kapil1502.releasetrain.domain;

  import jakarta.persistence.*;
  import lombok.Getter;
  import lombok.NoArgsConstructor;
  import lombok.Setter;

  import java.time.Instant;

  @Entity
  @Table(name = "deployments")
  @Getter
  @Setter
  @NoArgsConstructor
  public class DeploymentEntity {

      @Id
      @Column(nullable = false, updatable = false)
      private String deploymentId;

      @Column(nullable = false)
      private String serviceName;

      @Column(nullable = false)
      private String version;

      @Enumerated(EnumType.STRING)
      @Column(nullable = false)
      private DeploymentStatus status;

      @Column(nullable = false)
      private int currentStepIndex;

      @Column(nullable = false)
      private Instant createdAt;

      private Instant updatedAt;

      @PrePersist
      public void prePersist() {
          this.createdAt = Instant.now();
          this.updatedAt = Instant.now();
          if (this.status == null) {
              this.status = DeploymentStatus.CREATED;
          }
      }

      @PreUpdate
      public void preUpdate() {
          this.updatedAt = Instant.now();
      }
  }
  ```

### Step 3: Create Spring Data Repository (`repository/`)
* **`io.github.kapil1502.releasetrain.repository.DeploymentRepository.java`**
  ```java
  package io.github.kapil1502.releasetrain.repository;

  import io.github.kapil1502.releasetrain.domain.DeploymentEntity;
  import org.springframework.data.jpa.repository.JpaRepository;
  import org.springframework.stereotype.Repository;

  @Repository
  public interface DeploymentRepository extends JpaRepository<DeploymentEntity, String> {
  }
  ```

### Step 4: Implement State Machine Service (`service/`)
Enforce valid transitions according to the state diagram rules.

* **`io.github.kapil1502.releasetrain.service.RolloutStateMachineService.java`**
  ```java
  package io.github.kapil1502.releasetrain.service;

  import io.github.kapil1502.releasetrain.domain.DeploymentEntity;
  import io.github.kapil1502.releasetrain.domain.DeploymentStatus;
  import io.github.kapil1502.releasetrain.repository.DeploymentRepository;
  import org.springframework.stereotype.Service;
  import org.springframework.transaction.annotation.Transactional;

  @Service
  public class RolloutStateMachineService {

      private final DeploymentRepository deploymentRepository;

      public RolloutStateMachineService(DeploymentRepository deploymentRepository) {
          this.deploymentRepository = deploymentRepository;
      }

      @Transactional
      public DeploymentEntity transition(String deploymentId, DeploymentStatus targetStatus) {
          DeploymentEntity entity = deploymentRepository.findById(deploymentId)
              .orElseThrow(() -> new IllegalArgumentException("Deployment not found: " + deploymentId));

          validateTransition(entity.getStatus(), targetStatus);

          entity.setStatus(targetStatus);
          return deploymentRepository.save(entity);
      }

      private void validateTransition(DeploymentStatus current, DeploymentStatus target) {
          boolean valid = switch (current) {
              case CREATED -> target == DeploymentStatus.STEP_IN_PROGRESS || target == DeploymentStatus.FAILED;
              case STEP_IN_PROGRESS -> target == DeploymentStatus.HEALTH_CHECKING || target == DeploymentStatus.ROLLBACK_IN_PROGRESS;
              case HEALTH_CHECKING -> target == DeploymentStatus.STEP_COMPLETED || target == DeploymentStatus.ROLLBACK_IN_PROGRESS;
              case STEP_COMPLETED -> target == DeploymentStatus.STEP_IN_PROGRESS || target == DeploymentStatus.SUCCESSFUL;
              case ROLLBACK_IN_PROGRESS -> target == DeploymentStatus.FAILED;
              case SUCCESSFUL, FAILED -> false;
          };

          if (!valid) {
              throw new IllegalStateException("Invalid state transition from " + current + " to " + target);
          }
      }
  }
  ```

---

## 4. Verification & Testing Criteria
Claude Code must write a unit test using `@DataJpaTest` or H2 to verify that invalid state transitions throw an `IllegalStateException` and valid ones persist correctly.

* **`src/test/java/io/github/kapil1502/releasetrain/service/RolloutStateMachineServiceTest.java`**
  ```java
  package io.github.kapil1502.releasetrain.service;

  import io.github.kapil1502.releasetrain.domain.DeploymentEntity;
  import io.github.kapil1502.releasetrain.domain.DeploymentStatus;
  import io.github.kapil1502.releasetrain.repository.DeploymentRepository;
  import org.junit.jupiter.api.BeforeEach;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
  import org.springframework.context.annotation.Import;

  import static org.assertj.core.api.Assertions.assertThat;
  import static org.assertj.core.api.Assertions.assertThatThrownBy;

  @DataJpaTest
  @Import(RolloutStateMachineService.class)
  class RolloutStateMachineServiceTest {

      @Autowired
      private DeploymentRepository deploymentRepository;

      @Autowired
      private RolloutStateMachineService stateMachineService;

      @BeforeEach
      void setUp() {
          DeploymentEntity entity = new DeploymentEntity();
          entity.setDeploymentId("rel-100");
          entity.setServiceName("payment-service");
          entity.setVersion("1.0.0");
          entity.setStatus(DeploymentStatus.CREATED);
          entity.setCurrentStepIndex(0);
          deploymentRepository.save(entity);
      }

      @Test
      void shouldAllowValidTransition() {
          DeploymentEntity updated = stateMachineService.transition("rel-100", DeploymentStatus.STEP_IN_PROGRESS);
          assertThat(updated.getStatus()).isEqualTo(DeploymentStatus.STEP_IN_PROGRESS);
      }

      @Test
      void shouldRejectInvalidTransition() {
          assertThatThrownBy(() -> stateMachineService.transition("rel-100", DeploymentStatus.SUCCESSFUL))
              .isInstanceOf(IllegalStateException.class);
      }
  }
  ```

## 5. Acceptance Command
Run `./mvnw test` to verify database persistence and state machine rules.
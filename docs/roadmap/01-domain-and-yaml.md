# Roadmap Phase 1: Domain Models, YAML Configuration & Parser

## 1. Objective
Bootstrap the core data structures, immutable Java 17 records, Jackson YAML configuration mapper, and unit tests under the base package `io.github.kapil1502.releasetrain`.

**Platform:** Spring Boot 4.1 with Jackson 3 (`tools.jackson.*`). This phase adds two dependencies to `pom.xml`, both version-managed by the Spring Boot parent: `tools.jackson.dataformat:jackson-dataformat-yaml` and test-scoped `com.h2database:h2`.

---

## 2. Target File Structure
Claude Code must create or modify the following files during this phase:

```text
src/
├── main/
│   ├── java/
│   │   └── io/github/kapil1502/releasetrain/
│   │       ├── ReleaseTrainApplication.java
│   │       ├── config/
│   │       │   └── YamlConfig.java
│   │       ├── dto/
│   │       │   ├── DeploymentSpec.java
│   │       │   ├── RolloutStep.java
│   │       │   └── Strategy.java
│   │       └── service/
│   │           └── DeploymentConfigParser.java
│   └── resources/
│       └── application.yml
└── test/
    └── java/
        └── io/github/kapil1502/releasetrain/
            └── service/
                └── DeploymentConfigParserTest.java
```

---

## 3. Implementation Steps for Claude Code

### Step 1: Create Java 17 Immutable Records (`dto/`)
Create immutable records representing the declarative YAML deployment contract.

* **`io.github.kapil1502.releasetrain.dto.RolloutStep.java`**
  ```java
  package io.github.kapil1502.releasetrain.dto;

  public record RolloutStep(
      int step,
      int percentage,
      int pauseDurationSeconds
  ) {}
  ```

* **`io.github.kapil1502.releasetrain.dto.Strategy.java`**
  ```java
  package io.github.kapil1502.releasetrain.dto;

  import java.util.List;

  public record Strategy(
      List<RolloutStep> steps
  ) {}
  ```

* **`io.github.kapil1502.releasetrain.dto.DeploymentSpec.java`**
  ```java
  package io.github.kapil1502.releasetrain.dto;

  public record DeploymentSpec(
      String deploymentId,
      String serviceName,
      String version,
      Strategy strategy
  ) {}
  ```

### Step 2: Configure Jackson YAML Mapper Bean (`config/`)
Configure a Jackson 3 `YAMLMapper` to deserialize YAML files into Java 17 records safely. Record constructor binding and `java.time` support are built into Jackson 3, so no extra modules are registered. The bean must not be `@Primary`: Spring Boot's auto-configured `JsonMapper` stays the default for REST bodies and Kafka payloads.

* **`io.github.kapil1502.releasetrain.config.YamlConfig.java`**
  ```java
  package io.github.kapil1502.releasetrain.config;

  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;

  import tools.jackson.databind.DeserializationFeature;
  import tools.jackson.dataformat.yaml.YAMLMapper;

  @Configuration
  public class YamlConfig {

      @Bean(name = "yamlObjectMapper")
      public YAMLMapper yamlObjectMapper() {
          return YAMLMapper.builder()
                  // Jackson 3 ignores unknown properties by default; a misspelled key in a
                  // deployment spec must be rejected, not silently dropped.
                  .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                  .build();
      }
  }
  ```

### Step 3: Implement YAML Parser Service (`service/`)
Create a service that reads an input stream or file path and maps it to `DeploymentSpec`.

* **`io.github.kapil1502.releasetrain.service.DeploymentConfigParser.java`**
  ```java
  package io.github.kapil1502.releasetrain.service;

  import java.io.InputStream;

  import io.github.kapil1502.releasetrain.dto.DeploymentSpec;

  import org.springframework.stereotype.Service;

  import tools.jackson.core.JacksonException;
  import tools.jackson.dataformat.yaml.YAMLMapper;

  @Service
  public class DeploymentConfigParser {

      private final YAMLMapper yamlObjectMapper;

      public DeploymentConfigParser(YAMLMapper yamlObjectMapper) {
          this.yamlObjectMapper = yamlObjectMapper;
      }

      public DeploymentSpec parse(InputStream inputStream) {
          try {
              return yamlObjectMapper.readValue(inputStream, DeploymentSpec.class);
          } catch (JacksonException e) {
              throw new IllegalArgumentException("Failed to parse deployment YAML specification", e);
          }
      }
  }
  ```

---

## 4. Verification & Testing Criteria

Claude Code must write a unit test verifying successful parsing of a sample YAML configuration string, and that malformed YAML, unknown keys, and unreadable input are rejected with `IllegalArgumentException`.

* **`src/test/java/io/github/kapil1502/releasetrain/service/DeploymentConfigParserTest.java`**
  ```java
  package io.github.kapil1502.releasetrain.service;

  import io.github.kapil1502.releasetrain.config.YamlConfig;
  import io.github.kapil1502.releasetrain.dto.DeploymentSpec;
  import org.junit.jupiter.api.Test;
  import org.springframework.beans.factory.annotation.Autowired;
  import org.springframework.boot.test.context.SpringBootTest;

  import java.io.ByteArrayInputStream;
  import java.nio.charset.StandardCharsets;

  import static org.assertj.core.api.Assertions.assertThat;

  @SpringBootTest(classes = {YamlConfig.class, DeploymentConfigParser.class})
  class DeploymentConfigParserTest {

      @Autowired
      private DeploymentConfigParser parser;

      @Test
      void shouldParseValidDeploymentYaml() {
          String yaml = """
              deploymentId: "release-v2.4.0"
              serviceName: "payment-gateway"
              version: "2.4.0"
              strategy:
                steps:
                  - step: 1
                    percentage: 1
                    pauseDurationSeconds: 30
                  - step: 2
                    percentage: 100
                    pauseDurationSeconds: 0
              """;

          ByteArrayInputStream inputStream = new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
          DeploymentSpec spec = parser.parse(inputStream);

          assertThat(spec).isNotNull();
          assertThat(spec.deploymentId()).isEqualTo("release-v2.4.0");
          assertThat(spec.serviceName()).isEqualTo("payment-gateway");
          assertThat(spec.strategy().steps()).hasSize(2);
          assertThat(spec.strategy().steps().get(0).percentage()).isEqualTo(1);
      }
  }
  ```

## 5. Acceptance Command
Run `./mvnw test` to verify that all unit tests pass successfully before moving to Phase 2.
# Roadmap Phase 1: Domain Models, YAML Configuration & Parser

## 1. Objective
Bootstrap the core data structures, immutable Java 17 records, Jackson YAML configuration mapper, and unit tests under the base package `io.github.kapil1502.releasetrain`.

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
Configure Jackson to deserialize YAML files into Java 17 records safely, registering required modules (`ParameterNamesModule`, `JavaTimeModule`).

* **`io.github.kapil1502.releasetrain.config.YamlConfig.java`**
  ```java
  package io.github.kapil1502.releasetrain.config;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
  import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
  import com.fasterxml.jackson.module.paramnames.ParameterNamesModule;
  import org.springframework.context.annotation.Bean;
  import org.springframework.context.annotation.Configuration;
  import org.springframework.context.annotation.Primary;

  @Configuration
  public class YamlConfig {

      @Bean(name = "yamlObjectMapper")
      @Primary
      public ObjectMapper yamlObjectMapper() {
          ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
          mapper.registerModule(new ParameterNamesModule());
          mapper.registerModule(new JavaTimeModule());
          return mapper;
      }
  }
  ```

### Step 3: Implement YAML Parser Service (`service/`)
Create a service that reads an input stream or file path and maps it to `DeploymentSpec`.

* **`io.github.kapil1502.releasetrain.service.DeploymentConfigParser.java`**
  ```java
  package io.github.kapil1502.releasetrain.service;

  import com.fasterxml.jackson.databind.ObjectMapper;
  import io.github.kapil.releasetrain.dto.DeploymentSpec;
  import org.springframework.beans.factory.annotation.Qualifier;
  import org.springframework.stereotype.Service;

  import java.io.InputStream;

  @Service
  public class DeploymentConfigParser {

      private final ObjectMapper yamlObjectMapper;

      public DeploymentConfigParser(@Qualifier("yamlObjectMapper") ObjectMapper yamlObjectMapper) {
          this.yamlObjectMapper = yamlObjectMapper;
      }

      public DeploymentSpec parse(InputStream inputStream) {
          try {
              return yamlObjectMapper.readValue(inputStream, DeploymentSpec.class);
          } catch (Exception e) {
              throw new IllegalArgumentException("Failed to parse deployment YAML specification", e);
          }
      }
  }
  ```

---

## 4. Verification & Testing Criteria

Claude Code must write a unit test verifying successful parsing of a sample YAML configuration string.

* **`src/test/java/io/github/kapil1502/releasetrain/service/DeploymentConfigParserTest.java`**
  ```java
  package io.github.kapil1502.releasetrain.service;

  import io.github.kapil.releasetrain.config.YamlConfig;
  import io.github.kapil.releasetrain.dto.DeploymentSpec;
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
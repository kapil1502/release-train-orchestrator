package io.github.kapil1502.releasetrain.service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import io.github.kapil1502.releasetrain.config.YamlConfig;
import io.github.kapil1502.releasetrain.dto.DeploymentSpec;
import io.github.kapil1502.releasetrain.dto.RolloutStep;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.core.JacksonException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

		DeploymentSpec spec = parser.parse(toStream(yaml));

		assertThat(spec).isNotNull();
		assertThat(spec.deploymentId()).isEqualTo("release-v2.4.0");
		assertThat(spec.serviceName()).isEqualTo("payment-gateway");
		assertThat(spec.strategy().steps()).hasSize(2);
		assertThat(spec.strategy().steps().get(0).percentage()).isEqualTo(1);
	}

	@Test
	void shouldMapEveryFieldAndPreserveStepOrder() {
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
				      percentage: 5
				      pauseDurationSeconds: 60
				    - step: 3
				      percentage: 25
				      pauseDurationSeconds: 120
				    - step: 4
				      percentage: 100
				      pauseDurationSeconds: 0
				""";

		DeploymentSpec spec = parser.parse(toStream(yaml));

		assertThat(spec.version()).isEqualTo("2.4.0");
		assertThat(spec.strategy().steps()).containsExactly(
				new RolloutStep(1, 1, 30),
				new RolloutStep(2, 5, 60),
				new RolloutStep(3, 25, 120),
				new RolloutStep(4, 100, 0));
	}

	@Test
	void shouldRejectMalformedYaml() {
		String yaml = """
				deploymentId: "release-v2.4.0
				strategy: [
				""";

		assertParseFails(toStream(yaml));
	}

	@Test
	void shouldRejectUnknownProperty() {
		String yaml = """
				deploymentId: "release-v2.4.0"
				serviceName: "payment-gateway"
				version: "2.4.0"
				strategy:
				  steps:
				    - step: 1
				      percentge: 1
				      pauseDurationSeconds: 30
				""";

		assertParseFails(toStream(yaml));
	}

	@Test
	void shouldRejectNonNumericPercentage() {
		String yaml = """
				deploymentId: "release-v2.4.0"
				serviceName: "payment-gateway"
				version: "2.4.0"
				strategy:
				  steps:
				    - step: 1
				      percentage: "half"
				      pauseDurationSeconds: 30
				""";

		assertParseFails(toStream(yaml));
	}

	@Test
	void shouldRejectEmptyInput() {
		assertParseFails(toStream(""));
	}

	@Test
	void shouldWrapStreamReadFailure() {
		InputStream broken = new InputStream() {
			@Override
			public int read() throws IOException {
				throw new IOException("stream closed");
			}
		};

		assertParseFails(broken);
	}

	private void assertParseFails(InputStream inputStream) {
		assertThatThrownBy(() -> parser.parse(inputStream))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Failed to parse deployment YAML specification")
				.hasCauseInstanceOf(JacksonException.class);
	}

	private static InputStream toStream(String yaml) {
		return new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8));
	}

}

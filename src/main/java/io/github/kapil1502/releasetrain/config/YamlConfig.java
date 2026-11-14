package io.github.kapil1502.releasetrain.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import tools.jackson.databind.DeserializationFeature;
import tools.jackson.dataformat.yaml.YAMLMapper;

@Configuration
public class YamlConfig {

	// Deliberately not @Primary and typed as YAMLMapper: Spring Boot's JSON mapper must stay
	// the default for REST bodies and Kafka payloads.
	@Bean(name = "yamlObjectMapper")
	public YAMLMapper yamlObjectMapper() {
		return YAMLMapper.builder()
				// Jackson 3 ignores unknown properties by default; a misspelled key in a
				// deployment spec must be rejected, not silently dropped.
				.enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
				.build();
	}

}

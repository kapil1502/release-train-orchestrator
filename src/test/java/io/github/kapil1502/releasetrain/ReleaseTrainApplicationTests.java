package io.github.kapil1502.releasetrain;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.dataformat.yaml.YAMLMapper;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class ReleaseTrainApplicationTests {

	@Autowired
	private ObjectMapper defaultMapper;

	@Autowired
	private YAMLMapper yamlObjectMapper;

	@Test
	void contextLoads() {
	}

	@Test
	void yamlMapperDoesNotReplaceDefaultJsonMapper() {
		assertThat(defaultMapper).isInstanceOf(JsonMapper.class);
		assertThat(yamlObjectMapper).isNotSameAs(defaultMapper);
	}

}

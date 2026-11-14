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

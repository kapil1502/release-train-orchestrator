package io.github.kapil1502.releasetrain.dto;

public record DeploymentSpec(
		String deploymentId,
		String serviceName,
		String version,
		Strategy strategy
) {}

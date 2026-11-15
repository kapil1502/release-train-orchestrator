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

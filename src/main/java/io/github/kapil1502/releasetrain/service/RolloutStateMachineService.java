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

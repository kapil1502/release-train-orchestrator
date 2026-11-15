package io.github.kapil1502.releasetrain.service;

import java.util.Arrays;
import java.util.Set;
import java.util.stream.Stream;

import io.github.kapil1502.releasetrain.domain.DeploymentEntity;
import io.github.kapil1502.releasetrain.domain.DeploymentStatus;
import io.github.kapil1502.releasetrain.repository.DeploymentRepository;

import jakarta.persistence.EntityManager;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import static io.github.kapil1502.releasetrain.domain.DeploymentStatus.CREATED;
import static io.github.kapil1502.releasetrain.domain.DeploymentStatus.FAILED;
import static io.github.kapil1502.releasetrain.domain.DeploymentStatus.HEALTH_CHECKING;
import static io.github.kapil1502.releasetrain.domain.DeploymentStatus.ROLLBACK_IN_PROGRESS;
import static io.github.kapil1502.releasetrain.domain.DeploymentStatus.STEP_COMPLETED;
import static io.github.kapil1502.releasetrain.domain.DeploymentStatus.STEP_IN_PROGRESS;
import static io.github.kapil1502.releasetrain.domain.DeploymentStatus.SUCCESSFUL;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@Import(RolloutStateMachineService.class)
class RolloutStateMachineServiceTest {

	private static final String DEPLOYMENT_ID = "rel-100";

	// Edges of the state diagram in docs/ARCHITECTURE.md
	private static final Set<Arguments> VALID_TRANSITIONS = Set.of(
			Arguments.of(CREATED, STEP_IN_PROGRESS),
			Arguments.of(CREATED, FAILED),
			Arguments.of(STEP_IN_PROGRESS, HEALTH_CHECKING),
			Arguments.of(STEP_IN_PROGRESS, ROLLBACK_IN_PROGRESS),
			Arguments.of(HEALTH_CHECKING, STEP_COMPLETED),
			Arguments.of(HEALTH_CHECKING, ROLLBACK_IN_PROGRESS),
			Arguments.of(STEP_COMPLETED, STEP_IN_PROGRESS),
			Arguments.of(STEP_COMPLETED, SUCCESSFUL),
			Arguments.of(ROLLBACK_IN_PROGRESS, FAILED));

	@Autowired
	private DeploymentRepository deploymentRepository;

	@Autowired
	private RolloutStateMachineService stateMachineService;

	@Autowired
	private EntityManager entityManager;

	@ParameterizedTest(name = "{0} -> {1}")
	@MethodSource("validTransitions")
	void shouldPersistValidTransition(DeploymentStatus from, DeploymentStatus to) {
		saveDeployment(from);

		DeploymentEntity updated = stateMachineService.transition(DEPLOYMENT_ID, to);

		assertThat(updated.getStatus()).isEqualTo(to);
		assertThat(reloadDeployment().getStatus()).isEqualTo(to);
	}

	@ParameterizedTest(name = "{0} -> {1}")
	@MethodSource("invalidTransitions")
	void shouldRejectInvalidTransition(DeploymentStatus from, DeploymentStatus to) {
		saveDeployment(from);

		assertThatThrownBy(() -> stateMachineService.transition(DEPLOYMENT_ID, to))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("Invalid state transition from " + from + " to " + to);
		assertThat(reloadDeployment().getStatus()).isEqualTo(from);
	}

	@Test
	void shouldRejectNullTargetStatus() {
		saveDeployment(CREATED);

		assertThatThrownBy(() -> stateMachineService.transition(DEPLOYMENT_ID, null))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	void shouldRejectUnknownDeployment() {
		assertThatThrownBy(() -> stateMachineService.transition("missing", STEP_IN_PROGRESS))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessage("Deployment not found: missing");
	}

	@Test
	void shouldDriveFullRolloutToSuccess() {
		saveDeployment(CREATED);

		Stream.of(STEP_IN_PROGRESS, HEALTH_CHECKING, STEP_COMPLETED,
				STEP_IN_PROGRESS, HEALTH_CHECKING, STEP_COMPLETED, SUCCESSFUL)
				.forEach(next -> stateMachineService.transition(DEPLOYMENT_ID, next));

		assertThat(reloadDeployment().getStatus()).isEqualTo(SUCCESSFUL);
	}

	@Test
	void shouldDefaultStatusAndTimestampsOnPersist() {
		DeploymentEntity entity = newDeployment(null);

		deploymentRepository.saveAndFlush(entity);
		DeploymentEntity reloaded = reloadDeployment();

		assertThat(reloaded.getStatus()).isEqualTo(CREATED);
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isEqualTo(reloaded.getCreatedAt());
	}

	static Stream<Arguments> validTransitions() {
		return VALID_TRANSITIONS.stream();
	}

	static Stream<Arguments> invalidTransitions() {
		DeploymentStatus[] statuses = DeploymentStatus.values();
		return Arrays.stream(statuses)
				.flatMap(from -> Arrays.stream(statuses).map(to -> Arguments.of(from, to)))
				.filter(pair -> VALID_TRANSITIONS.stream()
						.noneMatch(valid -> Arrays.equals(valid.get(), pair.get())));
	}

	private void saveDeployment(DeploymentStatus status) {
		deploymentRepository.saveAndFlush(newDeployment(status));
	}

	private DeploymentEntity reloadDeployment() {
		entityManager.flush();
		entityManager.clear();
		return deploymentRepository.findById(DEPLOYMENT_ID).orElseThrow();
	}

	private static DeploymentEntity newDeployment(DeploymentStatus status) {
		DeploymentEntity entity = new DeploymentEntity();
		entity.setDeploymentId(DEPLOYMENT_ID);
		entity.setServiceName("payment-service");
		entity.setVersion("1.0.0");
		entity.setStatus(status);
		entity.setCurrentStepIndex(0);
		return entity;
	}

}

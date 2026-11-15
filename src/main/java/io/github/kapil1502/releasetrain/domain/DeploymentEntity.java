package io.github.kapil1502.releasetrain.domain;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "deployments")
@Getter
@Setter
@NoArgsConstructor
public class DeploymentEntity {

	@Id
	@Column(nullable = false, updatable = false)
	private String deploymentId;

	@Column(nullable = false)
	private String serviceName;

	@Column(nullable = false)
	private String version;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private DeploymentStatus status;

	@Column(nullable = false)
	private int currentStepIndex;

	@Column(nullable = false, updatable = false)
	private Instant createdAt;

	private Instant updatedAt;

	@PrePersist
	void prePersist() {
		Instant now = Instant.now();
		this.createdAt = now;
		this.updatedAt = now;
		if (this.status == null) {
			this.status = DeploymentStatus.CREATED;
		}
	}

	@PreUpdate
	void preUpdate() {
		this.updatedAt = Instant.now();
	}

}

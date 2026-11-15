package io.github.kapil1502.releasetrain.repository;

import io.github.kapil1502.releasetrain.domain.DeploymentEntity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeploymentRepository extends JpaRepository<DeploymentEntity, String> {

}

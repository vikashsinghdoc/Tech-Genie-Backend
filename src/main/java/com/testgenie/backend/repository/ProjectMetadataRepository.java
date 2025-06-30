package com.testgenie.backend.repository;

import com.testgenie.backend.entity.ProjectMetadata;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ProjectMetadataRepository extends JpaRepository<ProjectMetadata, Long> {
    Optional<ProjectMetadata> findByProjectName(String projectName);
}

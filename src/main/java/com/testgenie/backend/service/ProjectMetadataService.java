package com.testgenie.backend.service;

import com.testgenie.backend.entity.ProjectMetadata;
import com.testgenie.backend.repository.ProjectMetadataRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ProjectMetadataService {

    private final ProjectMetadataRepository repository;

    public ProjectMetadataService(ProjectMetadataRepository repository) {
        this.repository = repository;
    }

    public void saveMetadata(String projectName, int fileCount, long totalSize) {
        ProjectMetadata metadata = new ProjectMetadata(
                projectName,
                LocalDateTime.now(),
                fileCount,
                totalSize
        );
        repository.save(metadata);
    }
}

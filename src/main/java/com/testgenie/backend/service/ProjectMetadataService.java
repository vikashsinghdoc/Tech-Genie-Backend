package com.testgenie.backend.service;

import com.testgenie.backend.entity.ProjectMetadata;
import com.testgenie.backend.repository.ProjectMetadataRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ProjectMetadataService {

    private final ProjectMetadataRepository repository;

    public ProjectMetadataService(ProjectMetadataRepository repository) {
        this.repository = repository;
    }

    public void saveMetadata(String projectName, int fileCount, long totalSize, String hash) {
        ProjectMetadata metadata = new ProjectMetadata(
                projectName,
                LocalDateTime.now(),
                fileCount,
                totalSize,
                hash
        );
        repository.save(metadata);
    }

    public Optional<ProjectMetadata> findByProjectName(String name) {
        return repository.findByProjectName(name);
    }

    public void updateMetadata(String projectName, int fileCount, long totalSize, String hash) {
        Optional<ProjectMetadata> optional = repository.findByProjectName(projectName);
        if (optional.isPresent()) {
            ProjectMetadata meta = optional.get();
            meta.setFileCount(fileCount);
            meta.setTotalSize(totalSize);
            meta.setHash(hash);
            meta.setUploadTime(LocalDateTime.now());
            repository.save(meta);
        }
    }

}

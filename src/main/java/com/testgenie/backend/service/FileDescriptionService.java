package com.testgenie.backend.service;

import com.testgenie.backend.entity.FileDescription;
import com.testgenie.backend.repository.FileDescriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class FileDescriptionService {

    private static final Logger log = LoggerFactory.getLogger(FileDescriptionService.class);

    private final FileDescriptionRepository repository;

    public FileDescriptionService(FileDescriptionRepository repository) {
        this.repository = repository;
    }

    public void save(String project, String filePath, String description) {
        String path = (filePath == null) ? "" : filePath;
        String trimmed = (description == null) ? "" : description.trim();

        if (trimmed.isEmpty()) {
            repository.findByProjectNameAndFilePath(project, path).ifPresent(desc -> {
                repository.delete(desc);
                log.info("🗑️ Deleted description for project='{}', file='{}'", project, path.isEmpty() ? "(project)" : path);
            });
        } else {
            FileDescription entry = repository.findByProjectNameAndFilePath(project, path)
                    .orElseGet(() -> new FileDescription(null, project, path, trimmed));
            entry.setDescription(trimmed);
            repository.save(entry);
            log.info("💾 Saved description for project='{}', file='{}': Description: {}", project, path.isEmpty() ? "(project)" : path, trimmed);
        }
    }

    public Optional<String> get(String project, String filePath) {
        String path = (filePath == null) ? "" : filePath;
        return repository.findByProjectNameAndFilePath(project, path)
                .map(FileDescription::getDescription);
    }

    public Map<String, String> getAllDescriptionsForProject(String projectName) {
        return repository.findAll().stream()
                .filter(desc -> desc.getProjectName().equals(projectName))
                .collect(Collectors.toMap(
                        FileDescription::getFilePath,
                        FileDescription::getDescription
                ));
    }
}

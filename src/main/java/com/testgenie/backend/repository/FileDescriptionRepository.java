package com.testgenie.backend.repository;

import com.testgenie.backend.entity.FileDescription;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface FileDescriptionRepository extends JpaRepository<FileDescription, Long> {
    Optional<FileDescription> findByProjectNameAndFilePath(String projectName, String filePath);
}

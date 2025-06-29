package com.testgenie.backend.storage;

import com.testgenie.backend.config.StorageProperties;
import com.testgenie.backend.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

@Service
public class LocalFileStorageService implements FileStorageService {

    private final StorageProperties properties;
    private Path baseDir;

    public LocalFileStorageService(StorageProperties properties) {
        this.properties = properties;
    }

    @PostConstruct
    public void init() throws IOException {
        this.baseDir = Paths.get(properties.getBasePath()).toAbsolutePath().normalize();
        Files.createDirectories(baseDir);
    }

    @Override
    public Path saveZipFile(MultipartFile file) throws IOException {
        String filename = Optional.ofNullable(file.getOriginalFilename()).orElse("unknown.zip");
        String safeName = filename.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");
        Path targetPath = baseDir.resolve(safeName);
        file.transferTo(targetPath);
        return targetPath;
    }

    @Override
    public Path getProjectPath(String projectName) {
        return baseDir.resolve(projectName).normalize();
    }

    @Override
    public List<String> listAllProjects() throws IOException {
        try (Stream<Path> paths = Files.list(baseDir)) {
            return paths.filter(Files::isDirectory)
                    .map(path -> path.getFileName().toString())
                    .toList();
        }
    }

    @Override
    public Path getBaseDir() {
        return baseDir;
    }
}

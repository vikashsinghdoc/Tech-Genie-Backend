package com.testgenie.backend.storage;

import com.testgenie.backend.config.StorageProperties;
import com.testgenie.backend.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.*;
import java.util.Comparator;
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

    @Override
    public void replaceProject(String projectName, Path newExtractedPath) throws IOException {
        Path projectPath = getProjectPath(projectName);

        if (Files.exists(projectPath)) {
            deleteRecursively(projectPath);
        }

        Files.move(newExtractedPath, projectPath, StandardCopyOption.REPLACE_EXISTING);
    }

    @Override
    public void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }

    @Override
    public void saveNewProject(String projectName, Path sourceDir) throws IOException {
        Path targetPath = getProjectPath(projectName);
        deleteRecursively(targetPath); // clear if already exists
        Files.createDirectories(targetPath);

        Files.walk(sourceDir)
                .forEach(source -> {
                    try {
                        Path relative = sourceDir.relativize(source);
                        Path destination = targetPath.resolve(relative);
                        if (Files.isDirectory(source)) {
                            Files.createDirectories(destination);
                        } else {
                            Files.copy(source, destination, StandardCopyOption.REPLACE_EXISTING);
                        }
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });

        System.out.println("✅ Saved new project to local: " + targetPath);
    }

    // ✅ This is only needed to satisfy the interface. For local, just return the path directly.
    @Override
    public Path downloadZipToTemp(Path path) {
        return path; // It's already a real file path on disk
    }
}
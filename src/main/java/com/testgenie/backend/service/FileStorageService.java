package com.testgenie.backend.service;

import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public interface FileStorageService {
    Path saveZipFile(MultipartFile file) throws IOException;

    Path getProjectPath(String projectName);

    List<String> listAllProjects() throws IOException;

    Path getBaseDir();


    void replaceProject(String projectName, Path sourceDir) throws IOException;

    void saveNewProject(String projectName, Path sourceDir) throws IOException;

    void deleteRecursively(Path path) throws IOException;

    Path downloadZipToTemp(Path s3Path) throws IOException; // 👈 NEW METHOD
}
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

    // ✅ Add this to support replacing an existing project
    void replaceProject(String projectName, Path newExtractedPath) throws IOException;

    // ✅ Add this to support recursive delete during replacement
    void deleteRecursively(Path path) throws IOException;
}

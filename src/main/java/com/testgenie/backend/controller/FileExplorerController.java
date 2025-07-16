package com.testgenie.backend.controller;

import com.testgenie.backend.dto.FileNode;
import com.testgenie.backend.service.FileStorageService;
import com.testgenie.backend.storage.MinioFileStorageService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.File;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/files")
public class FileExplorerController {

    private final FileStorageService fileStorageService;

    public FileExplorerController(FileStorageService fileStorageService) {
        this.fileStorageService = fileStorageService;
    }

    @GetMapping("/tree")
    public ResponseEntity<?> getFileTree(@RequestParam("project") String projectName) {
        try {
            if (fileStorageService instanceof MinioFileStorageService minio) {
                // MinIO-specific logic
                String prefix = "projects/" + projectName + "/";
                ListObjectsV2Response response = minio.getS3Client().listObjectsV2(builder -> builder
                        .bucket(minio.getBucketName())
                        .prefix(prefix)
                        .build());

                FileNode root = new FileNode(projectName, "folder", projectName, new ArrayList<>());
                Map<String, FileNode> pathMap = new HashMap<>();
                pathMap.put(prefix, root);

                for (S3Object obj : response.contents()) {
                    String key = obj.key();
                    if (key.endsWith("/")) continue;

                    String relativeKey = key.substring(prefix.length());
                    String[] parts = relativeKey.split("/");
                    StringBuilder currentPath = new StringBuilder();
                    FileNode currentNode = root;

                    for (int i = 0; i < parts.length; i++) {
                        currentPath.append(parts[i]);
                        String fullPath = prefix + currentPath;

                        boolean isFile = (i == parts.length - 1);
                        if (!pathMap.containsKey(fullPath)) {
                            FileNode child = new FileNode(parts[i], isFile ? "file" : "folder",
                                    projectName + "/" + currentPath, new ArrayList<>());
                            pathMap.put(fullPath, child);
                            currentNode.children().add(child);
                        }

                        currentNode = pathMap.get(fullPath);
                        currentPath.append("/");
                    }
                }

                return ResponseEntity.ok(root);
            } else {
                // Local logic
                Path projectDir = fileStorageService.getProjectPath(projectName);
                if (!Files.exists(projectDir)) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Project not found.");
                }
                FileNode root = buildFileTree(projectDir.toFile(), projectName);
                return ResponseEntity.ok(root);
            }

        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error listing files.");
        }
    }

    @GetMapping("/content")
    public ResponseEntity<?> getFileContent(@RequestParam String path, @RequestParam String project) {
        try {
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
            Path filePath = Path.of(decodedPath).normalize();

            if (fileStorageService instanceof MinioFileStorageService minio) {
                String key = "projects/" + filePath.toString().replace("\\", "/");

                GetObjectRequest request = GetObjectRequest.builder()
                        .bucket(minio.getBucketName())
                        .key(key)
                        .build();

                try (ResponseInputStream<GetObjectResponse> s3Object = minio.getS3Client().getObject(request)) {
                    String content = new String(s3Object.readAllBytes(), StandardCharsets.UTF_8);
                    return ResponseEntity.ok(content);
                }
            } else {
                // Fallback: Local file read
                Path basePath = fileStorageService.getBaseDir();
                Path resolved = basePath.resolve(decodedPath).normalize();

                if (!resolved.startsWith(basePath)) {
                    return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied.");
                }

                if (!Files.exists(resolved) || Files.isDirectory(resolved)) {
                    return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found or is a directory.");
                }

                String content = Files.readString(resolved);
                return ResponseEntity.ok(content);
            }
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading file content.");
        }
    }


    // ✅ For local usage only
    private FileNode buildFileTree(File file, String relativePath) {
        String type = file.isDirectory() ? "folder" : "file";
        List<FileNode> children = new ArrayList<>();

        if (file.isDirectory()) {
            File[] listed = file.listFiles();
            if (listed != null) {
                for (File child : listed) {
                    String childPath = relativePath + "/" + child.getName();
                    children.add(buildFileTree(child, childPath));
                }
            }
        }

        return new FileNode(file.getName(), type, relativePath, children.isEmpty() ? null : children);
    }
}

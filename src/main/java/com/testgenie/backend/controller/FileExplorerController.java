package com.testgenie.backend.controller;

import com.testgenie.backend.dto.FileNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.File;
import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/files")
public class FileExplorerController {

    private final Path basePath = Paths.get(System.getProperty("user.dir"), "uploads");

    @GetMapping("/tree")
    public ResponseEntity<?> getFileTree(@RequestParam("project") String projectName) {
        Path projectDir = basePath.resolve(projectName).normalize();

        // Path traversal protection
        if (!projectDir.startsWith(basePath)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid project path.");
        }

        if (!Files.exists(projectDir) || !Files.isDirectory(projectDir)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Project not found.");
        }

        FileNode root = buildFileTree(projectDir.toFile(), projectName);
        return ResponseEntity.ok(root);
    }

    @GetMapping("/content")
    public ResponseEntity<?> getFileContent(@RequestParam String path) {
        try {
            String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
            Path filePath = basePath.resolve(decodedPath).normalize();

            // Prevent access outside /uploads
            if (!filePath.startsWith(basePath)) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Access denied.");
            }

            if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found or is a directory.");
            }

            String content = Files.readString(filePath);
            return ResponseEntity.ok(content);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error reading file.");
        }
    }

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

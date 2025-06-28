package com.testgenie.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/files")
public class FileExplorerController {

    private final String basePath = System.getProperty("user.dir") + "/uploads/";

    @GetMapping("/tree")
    public ResponseEntity<?> getFileTree(@RequestParam("project") String projectName) {
        File projectDir = new File(basePath + projectName);
        if (!projectDir.exists() || !projectDir.isDirectory()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Project not found.");
        }

        Map<String, Object> tree = buildFileTree(projectDir, "");
        return ResponseEntity.ok(tree);
    }

    @GetMapping("/content")
    public ResponseEntity<String> getFileContent(@RequestParam String path) throws IOException {
        String decodedPath = URLDecoder.decode(path, StandardCharsets.UTF_8);
        System.out.println("Decoded path: " + decodedPath);

        // Prepend the upload directory
        Path filePath = Paths.get(System.getProperty("user.dir"), "uploads", decodedPath);

        if (!Files.exists(filePath) || Files.isDirectory(filePath)) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("File not found or is a directory.");
        }

        String content = Files.readString(filePath);
        return ResponseEntity.ok(content);
    }


    private Map<String, Object> buildFileTree(File file, String currentPath) {
        Map<String, Object> node = new HashMap<>();
        node.put("name", file.getName());
        node.put("type", file.isDirectory() ? "folder" : "file");

        String newPath = currentPath.isEmpty() ? file.getName() : currentPath + "/" + file.getName();
        node.put("path", newPath);

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                List<Map<String, Object>> children = new ArrayList<>();
                for (File child : files) {
                    children.add(buildFileTree(child, newPath));
                }
                node.put("children", children);
            }
        }

        return node;
    }

}

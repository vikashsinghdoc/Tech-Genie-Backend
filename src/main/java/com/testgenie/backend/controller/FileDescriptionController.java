package com.testgenie.backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;

import java.io.*;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "http://localhost:5173")
public class FileDescriptionController {

    private static final Path BASE_UPLOAD_PATH = Paths.get(System.getProperty("user.dir"), "uploads");
    private final ObjectMapper mapper = new ObjectMapper();

    // POST: Save or update a file description
    @PostMapping("/describe")
    public ResponseEntity<Map<String, String>> saveDescription(@RequestBody Map<String, String> request) {
        String project = request.get("project");
        String filePath = request.get("file");
        String description = request.get("description");

        if (project == null || filePath == null || description == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields."));
        }

        Path metaDir = BASE_UPLOAD_PATH.resolve(project).resolve("meta").normalize();
        Path descFile = metaDir.resolve("descriptions.json");

        // Protect against invalid project path
        if (!metaDir.startsWith(BASE_UPLOAD_PATH)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project path."));
        }

        try {
            Files.createDirectories(metaDir);

            Map<String, String> descriptions = new HashMap<>();

            if (Files.exists(descFile)) {
                descriptions = mapper.readValue(descFile.toFile(), new TypeReference<>() {});
            }

            descriptions.put(filePath, description);
            mapper.writerWithDefaultPrettyPrinter().writeValue(descFile.toFile(), descriptions);

            return ResponseEntity.ok(Map.of("message", "Description saved."));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to save description."));
        }
    }

    // GET: Retrieve a file description
    @GetMapping("/describe")
    public ResponseEntity<Map<String, String>> getDescription(
            @RequestParam String project,
            @RequestParam String file) {

        Path descFile = BASE_UPLOAD_PATH.resolve(project).resolve("meta").resolve("descriptions.json").normalize();

        if (!descFile.startsWith(BASE_UPLOAD_PATH)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project path."));
        }

        try {
            if (!Files.exists(descFile)) {
                return ResponseEntity.ok(Map.of("description", ""));
            }

            Map<String, String> descriptions = mapper.readValue(descFile.toFile(), new TypeReference<>() {});
            String description = descriptions.getOrDefault(file, "");

            return ResponseEntity.ok(Map.of("description", description));
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to read description."));
        }
    }
}

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

    private static final String BASE_UPLOAD_PATH = System.getProperty("user.dir") + "/uploads";
    private final ObjectMapper mapper = new ObjectMapper();

    // POST /api/files/describe → Save or update a description
    @PostMapping("/describe")
    public ResponseEntity<String> saveDescription(@RequestBody Map<String, String> requestBody) {
        String project = requestBody.get("project");
        String file = requestBody.get("file");
        String description = requestBody.get("description");

        if (project == null || file == null || description == null) {
            return ResponseEntity.badRequest().body("Missing required fields.");
        }

        Path metaDir = Paths.get(BASE_UPLOAD_PATH, project, "meta");
        Path descFile = metaDir.resolve("descriptions.json");

        try {
            if (!Files.exists(metaDir)) {
                Files.createDirectories(metaDir);
            }

            Map<String, String> descriptions = new HashMap<>();

            if (Files.exists(descFile)) {
                descriptions = mapper.readValue(descFile.toFile(), new TypeReference<>() {});
            }

            descriptions.put(file, description);
            mapper.writerWithDefaultPrettyPrinter().writeValue(descFile.toFile(), descriptions);

            return ResponseEntity.ok("Saved");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to save description.");
        }
    }

    // GET /api/files/describe?project=...&file=... → Get description
    @GetMapping("/describe")
    public ResponseEntity<String> getDescription(@RequestParam String project, @RequestParam String file) {
        Path descFile = Paths.get(BASE_UPLOAD_PATH, project, "meta", "descriptions.json");

        try {
            if (!Files.exists(descFile)) {
                return ResponseEntity.ok(""); // Return empty if nothing saved yet
            }

            Map<String, String> descriptions = mapper.readValue(descFile.toFile(), new TypeReference<>() {});
            String description = descriptions.getOrDefault(file, "");

            return ResponseEntity.ok(description);
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Failed to read description.");
        }
    }
}

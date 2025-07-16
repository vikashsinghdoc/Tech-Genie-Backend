package com.testgenie.backend.controller;

import com.testgenie.backend.service.FileDescriptionService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/files")
@CrossOrigin(origins = "http://localhost:5173")
public class FileDescriptionController {

    private final FileDescriptionService descriptionService;

    public FileDescriptionController(FileDescriptionService descriptionService) {
        this.descriptionService = descriptionService;
    }

    @GetMapping("/descriptions")
    public ResponseEntity<Map<String, String>> getAllDescriptions(@RequestParam String project) {
        Map<String, String> descriptions = descriptionService.getAllDescriptionsForProject(project);
        return ResponseEntity.ok(descriptions);
    }

    @PostMapping("/describe")
    public ResponseEntity<Map<String, String>> saveDescription(@RequestBody Map<String, String> request) {
        String project = request.get("project");
        String filePath = request.get("file"); // optional (null or "" means project-level)
        String description = request.get("description");

        if (project == null || description == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing required fields."));
        }

        try {
            descriptionService.save(project, filePath, description);
            return ResponseEntity.ok(Map.of("message", "Description saved."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of("error", "Failed to save description."));
        }
    }


    // GET: Retrieve a file or project description
    @GetMapping("/describe")
    public ResponseEntity<Map<String, String>> getDescription(
            @RequestParam String project,
            @RequestParam(required = false) String file) {

        Optional<String> description = descriptionService.get(project, file);
        return ResponseEntity.ok(Map.of("description", description.orElse("")));
    }
}

package com.testgenie.backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class GenerateController {

    private static final Path BASE_UPLOAD_PATH = Paths.get(System.getProperty("user.dir"), "uploads");
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/generate")
    public ResponseEntity<Map<String, String>> generateCode(@RequestBody Map<String, String> body) {
        String project = body.get("project");
        String scenario = body.get("scenario");

        if (project == null || scenario == null) {
            return ResponseEntity.badRequest().body(Map.of("error", "Missing project or scenario."));
        }

        Path projectPath = BASE_UPLOAD_PATH.resolve(project).normalize();
        if (!projectPath.startsWith(BASE_UPLOAD_PATH)) {
            return ResponseEntity.badRequest().body(Map.of("error", "Invalid project path."));
        }

        try {
            // 1. Load descriptions.json
            Path descPath = projectPath.resolve("meta/descriptions.json");
            Map<String, String> descriptions = Files.exists(descPath)
                    ? mapper.readValue(descPath.toFile(), new TypeReference<>() {})
                    : new HashMap<>();

            // 2. Load described file contents (limit to ~10,000 chars)
            Map<String, String> fileContents = getDescribedFileContents(projectPath, descriptions, 10_000);

            // 3. Build AI prompt
            String prompt = buildPrompt(descriptions, fileContents, scenario);

            // 4. Send to Ollama
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);

            Map<String, Object> ollamaPayload = Map.of(
                    "model", "llama3",
                    "prompt", prompt,
                    "stream", false
            );

            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(ollamaPayload, headers);
            ResponseEntity<String> response = restTemplate.postForEntity(
                    "http://localhost:11434/api/generate", entity, String.class
            );

            return ResponseEntity.ok(Map.of("result", response.getBody()));

        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Failed to load project files."));
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Error generating code."));
        }
    }

    // Helper: Load described files up to character limit
    private Map<String, String> getDescribedFileContents(Path projectPath, Map<String, String> descriptions, int maxChars) throws IOException {
        Map<String, String> contents = new LinkedHashMap<>();
        int total = 0;

        for (Map.Entry<String, String> entry : descriptions.entrySet()) {
            String relativePath = entry.getKey();
            Path filePath = projectPath.resolve(relativePath).normalize();

            if (!filePath.startsWith(projectPath) || !Files.exists(filePath) || !relativePath.endsWith(".java")) {
                continue;
            }

            String content = Files.readString(filePath);
            if (total + content.length() > maxChars) break;

            contents.put(relativePath, content);
            total += content.length();
        }

        return contents;
    }

    // Helper: Construct prompt
    private String buildPrompt(Map<String, String> descriptions, Map<String, String> contents, String scenario) {
        StringBuilder prompt = new StringBuilder();
        prompt.append("You are an expert test automation assistant.\n");
        prompt.append("The user has uploaded a Java + Cucumber framework with the following file descriptions:\n\n");

        descriptions.forEach((file, desc) -> prompt
                .append("- File: ").append(file).append(" → ").append(desc).append("\n"));

        prompt.append("\nHere are the contents of those files:\n");

        contents.forEach((file, code) -> prompt
                .append("\nFile: ").append(file).append("\n")
                .append("------------------\n")
                .append(code).append("\n")
                .append("------------------\n"));

        prompt.append("\nBased on the above framework, generate Java code for the following Cucumber scenario:\n\n");
        prompt.append(scenario).append("\n\nRespond with complete Java code. Format cleanly and logically.");

        return prompt.toString();
    }
}

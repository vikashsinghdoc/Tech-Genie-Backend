package com.testgenie.backend.controller;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
public class GenerateController {

    private static final String BASE_UPLOAD_PATH = System.getProperty("user.dir") + "/uploads";
    private final ObjectMapper mapper = new ObjectMapper();
    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/generate")
    public ResponseEntity<String> generateCode(@RequestBody Map<String, String> body) {
        String project = body.get("project");
        String scenario = body.get("scenario");

        if (project == null || scenario == null) {
            return ResponseEntity.badRequest().body("Missing project or scenario.");
        }

        try {
            // 1. Load descriptions.json
            Path descPath = Paths.get(BASE_UPLOAD_PATH, project, "meta", "descriptions.json");
            Map<String, String> descriptions = new HashMap<>();
            if (Files.exists(descPath)) {
                descriptions = mapper.readValue(descPath.toFile(), new TypeReference<>() {});
            }

            // 2. Load described file contents (up to 10,000 characters total)
            Map<String, String> fileContents = getDescribedFileContents(project, descriptions, 10_000);

            // 3. Build AI prompt
            StringBuilder prompt = new StringBuilder();
            prompt.append("You are an expert test automation assistant.\n");
            prompt.append("The user has uploaded a Java + Cucumber framework with the following file descriptions:\n\n");

            for (Map.Entry<String, String> entry : descriptions.entrySet()) {
                prompt.append("- File: ").append(entry.getKey()).append(" → ").append(entry.getValue()).append("\n");
            }

            prompt.append("\nHere are the contents of those files:\n");
            for (Map.Entry<String, String> entry : fileContents.entrySet()) {
                prompt.append("\nFile: ").append(entry.getKey()).append("\n");
                prompt.append("------------------\n");
                prompt.append(entry.getValue()).append("\n");
                prompt.append("------------------\n");
            }

            prompt.append("\nBased on the above framework, generate Java code for the following Cucumber scenario:\n\n");
            prompt.append(scenario);
            prompt.append("\n\nRespond with complete Java code. Format cleanly and logically.");

            // 4. Prepare request to Ollama
            Map<String, Object> requestBody = Map.of(
                    "model", "llama3",
                    "prompt", prompt.toString(),
                    "stream", false
            );

            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);

            ResponseEntity<String> response = restTemplate.postForEntity("http://localhost:11434/api/generate", entity, String.class);

            return ResponseEntity.ok(response.getBody());

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Error generating code.");
        }
    }

    // Helper: Load described files up to character limit
    private Map<String, String> getDescribedFileContents(String project, Map<String, String> descriptions, int maxTotalChars) throws IOException {
        Map<String, String> fileContents = new LinkedHashMap<>();
        int totalChars = 0;

        for (Map.Entry<String, String> entry : descriptions.entrySet()) {
            String relativePath = entry.getKey();
            Path filePath = Paths.get(BASE_UPLOAD_PATH, project, relativePath);

            if (Files.exists(filePath) && relativePath.endsWith(".java")) {
                String content = Files.readString(filePath);
                if (totalChars + content.length() > maxTotalChars) break;

                fileContents.put(relativePath, content);
                totalChars += content.length();
            }
        }

        return fileContents;
    }
}

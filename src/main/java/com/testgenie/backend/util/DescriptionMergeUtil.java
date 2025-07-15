package com.testgenie.backend.util;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Map;

@Component
public class DescriptionMergeUtil {

    private final ObjectMapper mapper = new ObjectMapper();

    public int mergeDescriptions(Path oldProjectPath, Path newProjectPath) {
        Path oldDescPath = oldProjectPath.resolve("descriptions.json");
        Path newDescPath = newProjectPath.resolve("descriptions.json");

        if (!Files.exists(oldDescPath)) return 0;

        try {
            Map<String, String> oldDescriptions = loadDescriptions(oldDescPath);
            Map<String, String> newDescriptions = new HashMap<>();

            Files.walk(newProjectPath)
                    .filter(Files::isRegularFile)
                    .forEach(file -> {
                        String relativePath = newProjectPath.relativize(file).toString().replace("\\", "/");
                        if (oldDescriptions.containsKey(relativePath)) {
                            newDescriptions.put(relativePath, oldDescriptions.get(relativePath));
                        }
                    });

            mapper.writeValue(newDescPath.toFile(), newDescriptions);
            return newDescriptions.size(); // ✅ Count of preserved descriptions

        } catch (IOException e) {
            throw new RuntimeException("Failed to merge descriptions", e);
        }
    }


    private Map<String, String> loadDescriptions(Path path) throws IOException {
        return mapper.readValue(path.toFile(), new TypeReference<>() {});
    }
}

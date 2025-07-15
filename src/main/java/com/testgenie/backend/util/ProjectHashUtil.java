package com.testgenie.backend.util;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.*;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.stream.Collectors;

@Component
public class ProjectHashUtil {

    public String computeHash(Path rootPath) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");

        List<Path> files = Files.walk(rootPath)
                .filter(Files::isRegularFile)
                .filter(path -> !shouldSkip(path))
                .sorted()
                .collect(Collectors.toList());

        for (Path file : files) {
            byte[] content = Files.readAllBytes(file);
            digest.update(content);
        }

        return bytesToHex(digest.digest());
    }

    private boolean shouldSkip(Path path) {
        String name = path.toString().toLowerCase();
        return name.contains("node_modules")
                || name.endsWith(".class")
                || name.contains("__pycache__")
                || name.contains(".idea")
                || name.contains(".vscode")
                || name.contains("build")
                || name.contains("target");
    }

    private String bytesToHex(byte[] bytes) {
        StringBuilder hex = new StringBuilder();
        for (byte b : bytes) {
            hex.append(String.format("%02x", b));
        }
        return hex.toString();
    }
}

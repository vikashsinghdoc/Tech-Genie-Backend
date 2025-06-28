package com.testgenie.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173") // Adjust for frontend
public class FileUploadController {

    private static final Path UPLOAD_DIR = Paths.get(System.getProperty("user.dir"), "uploads");

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(@RequestParam("files") MultipartFile[] files) {
        try {
            if (!Files.exists(UPLOAD_DIR)) Files.createDirectories(UPLOAD_DIR);

            for (MultipartFile file : files) {
                String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("unknown.zip");

                // Sanitize filename
                String safeFileName = originalFilename.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");
                Path savedPath = UPLOAD_DIR.resolve(safeFileName).normalize();

                // Save file
                file.transferTo(savedPath.toFile());

                if (!safeFileName.toLowerCase().endsWith(".zip")) {
                    continue; // skip non-zip files
                }

                String projectName = safeFileName.substring(0, safeFileName.lastIndexOf('.'));
                Path extractTo = UPLOAD_DIR.resolve(projectName).normalize();

                // Ensure path safety
                if (!extractTo.startsWith(UPLOAD_DIR)) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid project name.");
                }

                Files.createDirectories(extractTo);
                unzip(savedPath, extractTo);

                return ResponseEntity.ok(Map.of("projectName", projectName));
            }

            return ResponseEntity.badRequest().body("No ZIP files found to process.");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("File upload or extraction failed.");
        }
    }

    @GetMapping("/uploaded-projects")
    public ResponseEntity<List<String>> listUploadedProjects() {
        try {
            if (!Files.exists(UPLOAD_DIR)) {
                return ResponseEntity.ok(Collections.emptyList());
            }

            try (var stream = Files.list(UPLOAD_DIR)) {
                List<String> projects = stream
                        .filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .toList();
                return ResponseEntity.ok(projects);
            }
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(List.of());
        }
    }

    private void unzip(Path zipPath, Path targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];

            while ((entry = zis.getNextEntry()) != null) {
                Path newPath = resolveSecureZipEntry(targetDir, entry.getName());

                if (entry.isDirectory()) {
                    Files.createDirectories(newPath);
                } else {
                    Files.createDirectories(newPath.getParent());
                    try (OutputStream fos = Files.newOutputStream(newPath)) {
                        int len;
                        while ((len = zis.read(buffer)) > 0) {
                            fos.write(buffer, 0, len);
                        }
                    }
                }
            }
        }
    }

    private Path resolveSecureZipEntry(Path targetDir, String entryName) throws IOException {
        Path resolvedPath = targetDir.resolve(entryName).normalize();
        if (!resolvedPath.startsWith(targetDir)) {
            throw new IOException("Entry is outside the target dir: " + entryName);
        }
        return resolvedPath;
    }
}

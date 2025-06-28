package com.testgenie.backend.controller;

import com.testgenie.backend.dto.ExtractionStatsDTO;
import com.testgenie.backend.dto.UploadResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
@Tag(name = "File Upload")
public class FileUploadController {

    @Value("${storage.base-path}")
    private String uploadBasePath;

    private Path UPLOAD_DIR;

    @PostConstruct
    public void init() {
        this.UPLOAD_DIR = Paths.get(uploadBasePath).toAbsolutePath().normalize();
    }

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    @Operation(summary = "Upload ZIP file(s) and extract project")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFiles(
            @Parameter(description = "ZIP file(s)", required = true)
            @RequestParam("files") List<MultipartFile> files) {
        try {
            if (!Files.exists(UPLOAD_DIR)) Files.createDirectories(UPLOAD_DIR);

            for (MultipartFile file : files) {
                String originalFilename = Optional.ofNullable(file.getOriginalFilename()).orElse("unknown.zip");
                String safeFileName = originalFilename.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");
                Path savedPath = UPLOAD_DIR.resolve(safeFileName).normalize();

                file.transferTo(savedPath.toFile());

                if (!safeFileName.toLowerCase().endsWith(".zip")) {
                    continue;
                }

                String projectName = safeFileName.substring(0, safeFileName.lastIndexOf('.'));
                Path extractTo = UPLOAD_DIR.resolve(projectName).normalize();

                if (!extractTo.startsWith(UPLOAD_DIR)) {
                    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Invalid project name.");
                }

                Files.createDirectories(extractTo);
                ExtractionStatsDTO stats = unzip(savedPath, extractTo);

                UploadResponseDTO response = new UploadResponseDTO(projectName, stats);
                return ResponseEntity.ok(response);
            }

            return ResponseEntity.badRequest().body("No ZIP files found to process.");
        } catch (IOException e) {
            logger.error("Upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload or extraction failed.");
        }
    }

    @GetMapping("/uploaded-projects")
    public ResponseEntity<List<String>> listUploadedProjects() {
        try {
            if (!Files.exists(UPLOAD_DIR)) return ResponseEntity.ok(Collections.emptyList());

            try (var stream = Files.list(UPLOAD_DIR)) {
                List<String> projects = stream
                        .filter(Files::isDirectory)
                        .map(path -> path.getFileName().toString())
                        .toList();
                return ResponseEntity.ok(projects);
            }
        } catch (IOException e) {
            logger.error("Listing projects failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(List.of());
        }
    }

    private ExtractionStatsDTO unzip(Path zipPath, Path targetDir) throws IOException {
        int extracted = 0;
        int skipped = 0;
        Map<String, Integer> skippedByType = new HashMap<>();

        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipPath.toFile()))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];

            while ((entry = zis.getNextEntry()) != null) {
                String entryName = entry.getName();
                String skipReason = getSkipReason(entryName);

                if (skipReason != null) {
                    skipped++;
                    skippedByType.merge(skipReason, 1, Integer::sum);
                    logger.info("⛔ Skipped: {} (reason: {})", entryName, skipReason);
                    zis.closeEntry();
                    continue;
                }

                Path newPath = resolveSecureZipEntry(targetDir, entryName);

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

                extracted++;
            }
        }

        return new ExtractionStatsDTO(extracted, skipped, skippedByType);
    }

    private String getSkipReason(String entryName) {
        String lowerName = entryName.toLowerCase();

        if (Arrays.stream(lowerName.split("/")).anyMatch(part -> part.startsWith("."))) {
            return ".hidden";
        }

        List<String> denyFolders = List.of(
                "node_modules/", "__pycache__/", "venv/", ".idea/",
                ".vscode/", "target/", "build/"
        );
        for (String folder : denyFolders) {
            if (lowerName.contains(folder)) {
                return folder.replace("/", "");
            }
        }

        List<String> denyExtensions = List.of(
                ".jar", ".class", ".exe", ".dll", ".so", ".bin",
                ".zip", ".tar", ".7z", ".rar",
                ".png", ".jpg", ".jpeg", ".gif", ".webp", ".ico",
                ".mp4", ".mp3", ".wav", ".mov", ".avi",
                ".log", ".pdf"
        );
        for (String ext : denyExtensions) {
            if (lowerName.endsWith(ext)) {
                return ext;
            }
        }

        return null;
    }

    private Path resolveSecureZipEntry(Path targetDir, String entryName) throws IOException {
        Path resolvedPath = targetDir.resolve(entryName).normalize();
        if (!resolvedPath.startsWith(targetDir)) {
            throw new IOException("Entry is outside target dir: " + entryName);
        }
        return resolvedPath;
    }
}

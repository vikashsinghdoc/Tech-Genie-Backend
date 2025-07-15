package com.testgenie.backend.controller;

import com.testgenie.backend.dto.ExtractionStatsDTO;
import com.testgenie.backend.dto.UploadResponseDTO;
import com.testgenie.backend.entity.ProjectMetadata;
import com.testgenie.backend.service.FileStorageService;
import com.testgenie.backend.service.ProjectMetadataService;
import com.testgenie.backend.util.DescriptionMergeUtil;
import com.testgenie.backend.util.ProjectHashUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.*;
import java.security.NoSuchAlgorithmException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173")
@Tag(name = "File Upload")
public class FileUploadController {

    private final FileStorageService fileStorageService;
    private final ProjectMetadataService projectMetadataService;
    private final ProjectHashUtil projectHashUtil;
    private final DescriptionMergeUtil descriptionMergeUtil;

    private static final Logger logger = LoggerFactory.getLogger(FileUploadController.class);

    public FileUploadController(FileStorageService fileStorageService,
                                ProjectMetadataService projectMetadataService,
                                ProjectHashUtil projectHashUtil,
                                DescriptionMergeUtil descriptionMergeUtil) {
        this.fileStorageService = fileStorageService;
        this.projectMetadataService = projectMetadataService;
        this.projectHashUtil = projectHashUtil;
        this.descriptionMergeUtil = descriptionMergeUtil;
    }

    @Operation(summary = "Upload ZIP file(s) and extract project")
    @PostMapping(value = "/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> uploadFiles(
            @Parameter(description = "ZIP file(s)", required = true)
            @RequestParam("files") List<MultipartFile> files) {
        try {
            for (MultipartFile file : files) {
                Path savedPath = fileStorageService.saveZipFile(file);
                String safeFileName = savedPath.getFileName().toString();

                if (!safeFileName.toLowerCase().endsWith(".zip")) {
                    continue;
                }

                String projectName = safeFileName.substring(0, safeFileName.lastIndexOf('.'));
                Path tempDir = Files.createTempDirectory("extract-" + projectName);
                ExtractionStatsDTO stats = unzip(savedPath, tempDir);

                long totalSize = Files.walk(tempDir)
                        .filter(Files::isRegularFile)
                        .mapToLong(p -> {
                            try {
                                return Files.size(p);
                            } catch (IOException e) {
                                return 0L;
                            }
                        })
                        .sum();

                String hash = projectHashUtil.computeHash(tempDir);
                Optional<ProjectMetadata> existingOpt = projectMetadataService.findByProjectName(projectName);

                if (existingOpt.isPresent()) {
                    ProjectMetadata existing = existingOpt.get();
                    if (hash.equals(existing.getHash())) {
                        deleteRecursively(tempDir);
                        return ResponseEntity.ok(new UploadResponseDTO("alreadyUploaded"));
                    } else {
                        Path oldPath = fileStorageService.getProjectPath(projectName);
                        int preservedCount = descriptionMergeUtil.mergeDescriptions(oldPath, tempDir);
                        fileStorageService.replaceProject(projectName, tempDir);
                        projectMetadataService.updateMetadata(projectName, stats.getExtracted(), totalSize, hash);
                        return ResponseEntity.ok(new UploadResponseDTO(projectName, stats, preservedCount));
                    }
                }

                // New project
                Path targetPath = fileStorageService.getProjectPath(projectName);
                Files.move(tempDir, targetPath, StandardCopyOption.REPLACE_EXISTING);
                projectMetadataService.saveMetadata(projectName, stats.getExtracted(), totalSize, hash);
                return ResponseEntity.ok(new UploadResponseDTO(projectName, stats));
            }

            return ResponseEntity.badRequest().body("No valid ZIP files to upload.");
        } catch (IOException | NoSuchAlgorithmException e) {
            logger.error("Upload failed", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("Upload or extraction failed.");
        }
    }

    @GetMapping("/uploaded-projects")
    public ResponseEntity<List<String>> listUploadedProjects() {
        try {
            List<String> projects = fileStorageService.listAllProjects();
            return ResponseEntity.ok(projects);
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
                "node_modules/", "__pycache__/", "venv/", ".idea/", ".vscode/", "target/", "build/"
        );
        for (String folder : denyFolders) {
            if (lowerName.contains(folder)) {
                return folder.replace("/", "");
            }
        }

        List<String> denyExtensions = List.of(
                ".jar", ".class", ".exe", ".dll", ".so", ".bin", ".zip", ".tar", ".7z", ".rar",
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

    private void deleteRecursively(Path path) throws IOException {
        if (Files.exists(path)) {
            Files.walk(path)
                    .sorted(Comparator.reverseOrder())
                    .forEach(p -> {
                        try {
                            Files.delete(p);
                        } catch (IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    });
        }
    }
}

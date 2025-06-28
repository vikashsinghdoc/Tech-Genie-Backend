package com.testgenie.backend.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "http://localhost:5173") // Frontend URL
public class FileUploadController {

    private static final String UPLOAD_DIR = System.getProperty("user.dir") + "/uploads/";

    @PostMapping("/upload")
    public ResponseEntity<?> uploadFiles(@RequestParam("files") MultipartFile[] files) {
        File uploadDir = new File(UPLOAD_DIR);
        if (!uploadDir.exists()) uploadDir.mkdirs();

        try {
            for (MultipartFile file : files) {
                String originalFilename = file.getOriginalFilename();
                if (originalFilename == null) originalFilename = "unknown.zip";

                String safeFileName = originalFilename.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");
                File savedFile = new File(UPLOAD_DIR + safeFileName);
                file.transferTo(savedFile);

                // Unzip logic
                if (safeFileName.toLowerCase().endsWith(".zip")) {
                    String baseName = safeFileName.substring(0, safeFileName.lastIndexOf('.'));
                    File extractTo = new File(UPLOAD_DIR + baseName + "/");
                    extractTo.mkdirs();

                    unzip(savedFile, extractTo);
                    System.out.println("Unzipped to: " + extractTo.getAbsolutePath());

                    // ✅ Return projectName as JSON
                    Map<String, String> response = new HashMap<>();
                    response.put("projectName", baseName);
                    String projectName = safeFileName.substring(0, safeFileName.lastIndexOf('.'));
                    return ResponseEntity.ok(Map.of("projectName", projectName));
                }
            }

            // If no zip found
            return ResponseEntity.badRequest().body("No zip file found to process.");
        } catch (IOException e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("File upload failed.");
        }
    }
    @GetMapping("/uploaded-projects")
    public ResponseEntity<List<String>> listUploadedProjects() {
        File uploadsDir = new File(System.getProperty("user.dir"), "uploads");
        if (!uploadsDir.exists() || !uploadsDir.isDirectory()) {
            return ResponseEntity.ok(Collections.emptyList());
        }

        String[] projectNames = uploadsDir.list((dir, name) -> new File(dir, name).isDirectory());
        return ResponseEntity.ok(projectNames == null ? List.of() : Arrays.asList(projectNames));
    }


    private void unzip(File zipFile, File targetDir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(zipFile))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];

            while ((entry = zis.getNextEntry()) != null) {
                File newFile = new File(targetDir, entry.getName());

                if (entry.isDirectory()) {
                    newFile.mkdirs();
                    continue;
                }

                // Create parent folders if needed
                new File(newFile.getParent()).mkdirs();

                try (FileOutputStream fos = new FileOutputStream(newFile)) {
                    int len;
                    while ((len = zis.read(buffer)) > 0) {
                        fos.write(buffer, 0, len);
                    }
                } catch (FileNotFoundException e) {
                    throw new RuntimeException(e);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
        }
    }
}

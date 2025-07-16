package com.testgenie.backend.storage;

import com.testgenie.backend.config.StorageProperties;
import com.testgenie.backend.service.FileStorageService;
import jakarta.annotation.PostConstruct;
import lombok.Getter;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

@Service
@Getter
@Primary
public class MinioFileStorageService implements FileStorageService {

    private final S3Client s3Client;
    private final StorageProperties properties;
    private String bucketName;

    private static final org.slf4j.Logger logger = getLogger(MinioFileStorageService.class);

    public MinioFileStorageService(S3Client s3Client, StorageProperties properties) {
        this.s3Client = s3Client;
        this.properties = properties;
    }

    @PostConstruct
    public void init() {
        this.bucketName = Optional.ofNullable(properties.getBasePath()).orElse("testgenie-bucket");
        System.out.println("🪣 Using bucket: " + bucketName);

        boolean exists = s3Client.listBuckets().buckets().stream()
                .anyMatch(b -> b.name().equals(bucketName));

        if (!exists) {
            System.out.println("📦 Creating bucket: " + bucketName);
            s3Client.createBucket(CreateBucketRequest.builder().bucket(bucketName).build());
        } else {
            System.out.println("✅ Bucket already exists: " + bucketName);
        }
    }

    @Override
    public Path saveZipFile(MultipartFile file) throws IOException {
        String fileName = Optional.ofNullable(file.getOriginalFilename()).orElse("unknown.zip");
        String safeName = fileName.replaceAll("[^a-zA-Z0-9\\.\\-_]", "_");

        PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                .bucket(bucketName)
                .key("zips/" + safeName) // ✅ Changed from "uploads/"
                .contentType(file.getContentType())
                .build();

        s3Client.putObject(putObjectRequest, RequestBody.fromInputStream(file.getInputStream(), file.getSize()));

        return Path.of("s3://" + bucketName + "/zips/" + safeName); // ✅ Also updated to reflect "zips"
    }


    @Override
    public Path getProjectPath(String projectName) {
        return Path.of("projects/" + projectName);
    }

    @Override
    public List<String> listAllProjects() {
        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix("projects/")
                .build();

        ListObjectsV2Response response = s3Client.listObjectsV2(listRequest);

        return response.contents().stream()
                .map(S3Object::key)
                .map(key -> {
                    String path = key.substring("projects/".length());
                    int slashIndex = path.indexOf('/');
                    return (slashIndex != -1) ? path.substring(0, slashIndex) : path;
                })
                .filter(name -> !name.isBlank())
                .distinct()
                .toList();
    }

    @Override
    public Path getBaseDir() {
        return Path.of("s3://" + bucketName);
    }

    @Override
    public void replaceProject(String projectName, Path sourceDir) throws IOException {
        deleteProject(projectName);
        saveNewProject(projectName, sourceDir);
    }

    private void deleteProject(String projectName) {
        String prefix = "projects/" + projectName + "/";

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

        if (listResponse.hasContents()) {
            List<ObjectIdentifier> toDelete = listResponse.contents().stream()
                    .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                    .toList();

            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(toDelete).build())
                    .build();

            s3Client.deleteObjects(deleteRequest);
        }
    }

    @Override
    public void saveNewProject(String projectName, Path sourceDir) throws IOException {
        String prefix = "projects/" + projectName + "/";
        
        Files.walk(sourceDir)
                .filter(Files::isRegularFile)
                .forEach(file -> {
                    try {
                        String relativePath = sourceDir.relativize(file).toString().replace("\\", "/");
                        String objectKey = prefix + relativePath;

                        s3Client.putObject(
                                PutObjectRequest.builder()
                                        .bucket(bucketName)
                                        .key(objectKey)
                                        .contentType(Files.probeContentType(file))
                                        .build(),
                                RequestBody.fromFile(file)
                        );
                    } catch (IOException e) {
                        throw new UncheckedIOException(e);
                    }
                });

        System.out.println("✅ Saved new project: " + projectName);
    }

    @Override
    public void deleteRecursively(Path path) {
        String prefix = extractS3Prefix(path);

        ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                .bucket(bucketName)
                .prefix(prefix)
                .build();

        ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);

        if (listResponse.hasContents()) {
            List<ObjectIdentifier> toDelete = listResponse.contents().stream()
                    .map(obj -> ObjectIdentifier.builder().key(obj.key()).build())
                    .toList();

            DeleteObjectsRequest deleteRequest = DeleteObjectsRequest.builder()
                    .bucket(bucketName)
                    .delete(Delete.builder().objects(toDelete).build())
                    .build();

            s3Client.deleteObjects(deleteRequest);
        }
    }

    private String extractS3Prefix(Path path) {
        String full = path.toString();
        if (full.startsWith("s3://")) {
            full = full.substring(5); // remove s3://
            int firstSlash = full.indexOf("/");
            return firstSlash > 0 ? full.substring(firstSlash + 1) : full;
        }
        return full;
    }

    @Override
    public Path downloadZipToTemp(Path s3Path) throws IOException {
        String key = "zips/" + s3Path.getFileName().toString(); // ✅ Force consistent prefix
        Path tempFile = Files.createTempFile("download-", ".zip");

        GetObjectRequest getRequest = GetObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .build();

        try (ResponseInputStream<GetObjectResponse> s3Input = s3Client.getObject(getRequest)) {
            Files.copy(s3Input, tempFile, StandardCopyOption.REPLACE_EXISTING);
        }

        return tempFile;
    }
    public Path tempFileForKey(String key) throws IOException {
        Path temp = Files.createTempFile("s3-read-", "-" + Paths.get(key).getFileName().toString());
        s3Client.getObject(GetObjectRequest.builder().bucket(bucketName).key(key).build(), temp);
        return temp;
    }


}
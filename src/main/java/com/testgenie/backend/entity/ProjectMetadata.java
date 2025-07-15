package com.testgenie.backend.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@Entity
@Table(name = "project_metadata")
public class ProjectMetadata {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "project_name", nullable = false, unique = true)
    private String projectName;

    @Column(name = "upload_time", nullable = false)
    private LocalDateTime uploadTime;

    @Column(name = "file_count")
    private int fileCount;

    @Column(name = "total_size")
    private long totalSize;

    @Column(name = "hash")
    private String hash;

    // === Constructors ===

    public ProjectMetadata() {}

    // Constructor with all fields (Optional but useful)
    public ProjectMetadata(String projectName, LocalDateTime uploadTime, int fileCount, long totalSize, String hash) {
        this.projectName = projectName;
        this.uploadTime = uploadTime;
        this.fileCount = fileCount;
        this.totalSize = totalSize;
        this.hash = hash;
    }

    // Constructor without hash (optional if you're setting it later via setter)
    public ProjectMetadata(String projectName, LocalDateTime uploadTime, int fileCount, long totalSize) {
        this.projectName = projectName;
        this.uploadTime = uploadTime;
        this.fileCount = fileCount;
        this.totalSize = totalSize;
    }
}

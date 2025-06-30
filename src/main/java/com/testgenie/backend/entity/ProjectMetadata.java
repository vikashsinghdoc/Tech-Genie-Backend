package com.testgenie.backend.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

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

    // Constructors, Getters, Setters

    public ProjectMetadata() {}

    public ProjectMetadata(String projectName, LocalDateTime uploadTime, int fileCount, long totalSize) {
        this.projectName = projectName;
        this.uploadTime = uploadTime;
        this.fileCount = fileCount;
        this.totalSize = totalSize;
    }

    // Getters & Setters here...
}

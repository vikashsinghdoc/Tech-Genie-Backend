package com.testgenie.backend.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "file_descriptions")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileDescription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String projectName;

    @Column(nullable = false)
    private String filePath; // "" for project-level description

    @Column(length = 2000)
    private String description;
}

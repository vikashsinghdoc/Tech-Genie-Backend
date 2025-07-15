package com.testgenie.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UploadResponseDTO {
    private String projectName;
    private ExtractionStatsDTO stats;
    private Integer preservedDescriptions;

    public UploadResponseDTO(String message) {
        this.projectName = message;
    }

    public UploadResponseDTO(String projectName, ExtractionStatsDTO stats) {
        this.projectName = projectName;
        this.stats = stats;
    }
}

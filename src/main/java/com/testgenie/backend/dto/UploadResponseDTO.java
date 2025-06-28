package com.testgenie.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UploadResponseDTO {
    private String projectName;
    private ExtractionStatsDTO stats;
}

package com.testgenie.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.Map;

@Data
@AllArgsConstructor
public class ExtractionStatsDTO {
    private int extracted;
    private int skipped;
    private Map<String, Integer> skippedByType;
}

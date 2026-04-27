package com.wwa.agenthub.contracts.dto;

import java.util.List;

public record CsvCompareResponseDto(
        String baseFileName,
        int fileCount,
        List<CsvCompareFileResultDto> comparisons,
        long totalDifferences,
        boolean truncated) {
}

package com.wwa.agenthub.contracts.dto;

import java.util.List;

public record CsvCompareFileResultDto(
        String fileName,
        List<String> headers,
        long matchedRows,
        long changedRows,
        long addedRows,
        long removedRows,
        long totalDifferences,
        boolean truncated,
        List<CsvDifferenceDto> differences) {
}

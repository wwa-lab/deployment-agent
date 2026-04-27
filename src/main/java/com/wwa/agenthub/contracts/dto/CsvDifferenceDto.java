package com.wwa.agenthub.contracts.dto;

import java.util.List;

public record CsvDifferenceDto(
        long rowNumber,
        String type,
        String column,
        String baseValue,
        String compareValue,
        List<String> baseRow,
        List<String> compareRow) {
}

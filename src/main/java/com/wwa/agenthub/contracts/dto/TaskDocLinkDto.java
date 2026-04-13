package com.wwa.agenthub.contracts.dto;

public record TaskDocLinkDto(
        String label,
        String url,
        String note,
        Boolean required
) {
}

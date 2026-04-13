package com.wwa.deploymentagent.contracts.dto;

public record TaskDocLinkDto(
        String label,
        String url,
        String note,
        Boolean required
) {
}

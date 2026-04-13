package com.wwa.deploymentagent.contracts.dto;

import java.util.List;

public record TaskDocsUpdateRequestDto(
        List<TaskDocLinkDto> inputs,
        List<TaskDocLinkDto> outputs
) {
}

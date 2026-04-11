package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.domain.fileimport.ImportResult;

public record UploadResponseDto(
        String releaseFlowId,
        String releaseId,
        String stage,
        int taskCount,
        String snowGroup,
        String application,
        String agent
) {

    public static UploadResponseDto from(ImportResult result) {
        return new UploadResponseDto(
                result.releaseFlowId(),
                result.releaseId(),
                result.stage(),
                result.taskCount(),
                result.snowGroup(),
                result.application(),
                result.agent());
    }
}

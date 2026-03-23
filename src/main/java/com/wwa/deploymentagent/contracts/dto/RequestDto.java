package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.domain.releaseflow.Request;

import java.util.List;

public record RequestDto(
        String id,
        String releaseFlowId,
        Stage stage,
        RequestStatus requestStatus,
        String snowGroup,
        String application,
        String site,
        String createdBy,
        Integer estimatedRemainingMinutes,
        java.time.Instant createdAt,
        java.time.Instant updatedAt,
        long version,
        List<TaskDto> tasks
) {
    public static RequestDto from(Request request, List<TaskDto> tasks) {
        return new RequestDto(
                request.getId(),
                request.getReleaseFlow().getId(),
                request.getStage(),
                request.getRequestStatus(),
                request.getSnowGroup(),
                request.getApplication(),
                request.getSite(),
                request.getCreatedBy(),
                request.getEstimatedRemainingMinutes(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getVersion() != null ? request.getVersion() : 0L,
                tasks
        );
    }
}

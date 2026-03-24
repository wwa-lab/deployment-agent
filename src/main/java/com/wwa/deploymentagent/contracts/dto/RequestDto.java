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
        String agent,
        String owner,
        String site,
        String createdBy,
        Integer estimatedRemainingMinutes,
        java.time.Instant archivedAt,
        String archivedBy,
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
                request.getAgent(),
                request.getOwner(),
                request.getSite(),
                request.getCreatedBy(),
                request.getEstimatedRemainingMinutes(),
                request.getArchivedAt(),
                request.getArchivedBy(),
                request.getCreatedAt(),
                request.getUpdatedAt(),
                request.getVersion() != null ? request.getVersion() : 0L,
                tasks
        );
    }
}

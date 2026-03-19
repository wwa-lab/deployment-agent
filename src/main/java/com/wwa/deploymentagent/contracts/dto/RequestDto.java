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
        List<TaskDto> tasks
) {
    public static RequestDto from(Request request, List<TaskDto> tasks) {
        return new RequestDto(
                request.getId(),
                request.getReleaseFlow().getId(),
                request.getStage(),
                request.getRequestStatus(),
                tasks
        );
    }
}

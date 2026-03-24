package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.ReviewStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.Request;

import java.util.Comparator;
import java.util.List;

public record ReleaseFlowListItemDto(
        String id,
        String projectId,
        String projectName,
        String releaseId,
        String normalizedReleaseId,
        Stage currentStage,
        FlowStatus flowStatus,
        ReviewStatus reviewStatus,
        java.time.Instant archivedAt,
        String archivedBy,
        String application,
        String snowGroup,
        String agent,
        String owner,
        RequestStatus sitStatus,
        RequestStatus uatStatus,
        RequestStatus prodStatus
) {
    public static ReleaseFlowListItemDto from(ReleaseFlow rf, List<Request> requests) {
        Request scopeRequest = scopeRequestFor(rf, requests);
        return new ReleaseFlowListItemDto(
                rf.getId(),
                rf.getProjectId(),
                rf.getProjectName(),
                rf.getReleaseId(),
                rf.getNormalizedReleaseId(),
                rf.getCurrentStage(),
                rf.getFlowStatus(),
                rf.getReviewStatus(),
                rf.getArchivedAt(),
                rf.getArchivedBy(),
                scopeRequest != null && scopeRequest.getApplication() != null
                        ? scopeRequest.getApplication()
                        : rf.getProjectName(),
                scopeRequest != null ? scopeRequest.getSnowGroup() : null,
                scopeRequest != null ? scopeRequest.getAgent() : null,
                scopeRequest != null ? scopeRequest.getOwner() : null,
                requestStatusFor(requests, Stage.SIT),
                requestStatusFor(requests, Stage.UAT),
                requestStatusFor(requests, Stage.PROD)
        );
    }

    private static Request scopeRequestFor(ReleaseFlow rf, List<Request> requests) {
        if (requests == null || requests.isEmpty()) {
            return null;
        }

        return requests.stream()
                .filter(request -> request.getArchivedAt() == null)
                .filter(request -> request.getStage() == rf.getCurrentStage())
                .max(Comparator.comparing(Request::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .or(() -> requests.stream()
                        .filter(request -> request.getArchivedAt() == null)
                        .max(Comparator.comparing(Request::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))))
                .orElse(requests.get(0));
    }

    private static RequestStatus requestStatusFor(List<Request> requests, Stage stage) {
        if (requests == null || requests.isEmpty()) {
            return RequestStatus.Pending;
        }

        return requests.stream()
                .filter(request -> request.getStage() == stage)
                .sorted((left, right) -> {
                    boolean leftArchived = left.getArchivedAt() != null;
                    boolean rightArchived = right.getArchivedAt() != null;
                    return Boolean.compare(leftArchived, rightArchived);
                })
                .map(Request::getRequestStatus)
                .findFirst()
                .orElse(RequestStatus.Pending);
    }
}

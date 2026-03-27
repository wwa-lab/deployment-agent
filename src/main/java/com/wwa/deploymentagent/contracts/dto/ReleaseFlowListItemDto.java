package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.ReviewStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowAggregation;
import com.wwa.deploymentagent.domain.releaseflow.Request;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

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
        RequestStatus prodStatus,
        boolean sitPresent,
        boolean uatPresent,
        boolean prodPresent,
        boolean stitched,
        int linkedReleaseCount,
        List<String> linkedReleaseIds,
        List<String> linkedReleaseFlowIds
) {
    public static final String ATTEMPT_VIEW_LATEST = "latest";
    public static final String ATTEMPT_VIEW_HISTORY = "history";

    public static ReleaseFlowListItemDto from(ReleaseFlow rf, List<Request> requests) {
        return from(rf, requests, ATTEMPT_VIEW_LATEST);
    }

    public static ReleaseFlowListItemDto from(ReleaseFlow rf, List<Request> requests, String attemptView) {
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
                requestStatusFor(requests, Stage.SIT, attemptView),
                requestStatusFor(requests, Stage.UAT, attemptView),
                requestStatusFor(requests, Stage.PROD, attemptView),
                hasStage(requests, Stage.SIT),
                hasStage(requests, Stage.UAT),
                hasStage(requests, Stage.PROD),
                false,
                1,
                List.of(rf.getReleaseId()),
                List.of(rf.getId())
        );
    }

    private static Request scopeRequestFor(ReleaseFlow rf, List<Request> requests) {
        if (requests == null || requests.isEmpty()) {
            return null;
        }

        return requests.stream()
                .filter(request -> request.getArchivedAt() == null)
                .filter(request -> request.getStage() == rf.getCurrentStage())
                .max(requestAttemptComparator())
                .or(() -> requests.stream()
                        .filter(request -> request.getArchivedAt() == null)
                        .max(requestAttemptComparator()))
                .orElse(requests.get(0));
    }

    private static RequestStatus requestStatusFor(List<Request> requests, Stage stage, String attemptView) {
        if (requests == null || requests.isEmpty()) {
            return RequestStatus.Pending;
        }

        List<Request> stageRequests = stageRequestsByAttemptView(requests, stage, attemptView);
        if (stageRequests.isEmpty()) {
            return RequestStatus.Pending;
        }

        return ReleaseFlowAggregation.aggregateRequestsToStageStatus(
                stageRequests.stream().map(Request::getRequestStatus).toList());
    }

    private static boolean hasStage(List<Request> requests, Stage stage) {
        if (requests == null || requests.isEmpty()) {
            return false;
        }

        return requests.stream().anyMatch(request -> request.getStage() == stage);
    }

    private static List<Request> stageRequestsByAttemptView(List<Request> requests, Stage stage, String attemptView) {
        List<Request> stageRequests = requests.stream()
                .filter(request -> request.getStage() == stage)
                .toList();
        if (stageRequests.isEmpty()) {
            return List.of();
        }
        if (!ATTEMPT_VIEW_HISTORY.equalsIgnoreCase(normalizeAttemptView(attemptView))) {
            return latestRequestsPerStage(stageRequests);
        }
        return stageRequests;
    }

    private static List<Request> latestRequestsPerStage(List<Request> requests) {
        Map<Stage, Request> latestByStage = requests.stream()
                .collect(Collectors.toMap(
                        Request::getStage,
                        request -> request,
                        (left, right) -> requestAttemptComparator().compare(left, right) >= 0 ? left : right));
        return latestByStage.values().stream().toList();
    }

    private static Comparator<Request> requestAttemptComparator() {
        return Comparator
                .comparing(Request::getAttemptNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Request::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private static String normalizeAttemptView(String attemptView) {
        if (attemptView == null || attemptView.isBlank()) {
            return ATTEMPT_VIEW_LATEST;
        }
        return attemptView.trim().toLowerCase(Locale.ROOT);
    }
}

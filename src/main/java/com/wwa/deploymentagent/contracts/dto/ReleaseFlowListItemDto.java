package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.ReviewStatus;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowAggregation;
import com.wwa.deploymentagent.domain.releaseflow.Request;

import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public record ReleaseFlowListItemDto(
        String id,
        String projectId,
        String projectName,
        String releaseId,
        String normalizedReleaseId,
        String currentStage,
        FlowStatus flowStatus,
        ReviewStatus reviewStatus,
        java.time.Instant archivedAt,
        String archivedBy,
        String application,
        String snowGroup,
        String agent,
        String owner,
        Map<String, RequestStatus> stageStatuses,
        Set<String> stagesPresent,
        boolean stitched,
        int linkedReleaseCount,
        List<String> linkedReleaseIds,
        List<String> linkedReleaseFlowIds
) {
    public static final String ATTEMPT_VIEW_LATEST = "latest";
    public static final String ATTEMPT_VIEW_HISTORY = "history";

    public ReleaseFlowListItemDto {
        stageStatuses = stageStatuses == null
                ? Collections.emptyMap()
                : Collections.unmodifiableMap(new LinkedHashMap<>(stageStatuses));
        stagesPresent = stagesPresent == null
                ? Collections.emptySet()
                : Collections.unmodifiableSet(new LinkedHashSet<>(stagesPresent));
    }

    public static ReleaseFlowListItemDto from(ReleaseFlow rf, List<Request> requests) {
        return from(rf, requests, ATTEMPT_VIEW_LATEST);
    }

    public static ReleaseFlowListItemDto from(ReleaseFlow rf, List<Request> requests, String attemptView) {
        Request scopeRequest = scopeRequestFor(rf, requests);
        Set<String> observedStages = observedStages(requests);
        Map<String, RequestStatus> statuses = buildStageStatuses(observedStages, requests, attemptView);

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
                statuses,
                observedStages,
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
                .filter(request -> rf.getCurrentStage().equals(request.getStage()))
                .max(requestAttemptComparator())
                .or(() -> requests.stream()
                        .filter(request -> request.getArchivedAt() == null)
                        .max(requestAttemptComparator()))
                .orElse(requests.get(0));
    }

    private static Set<String> observedStages(List<Request> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptySet();
        }
        return requests.stream()
                .map(Request::getStage)
                .filter(stage -> stage != null && !stage.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private static Map<String, RequestStatus> buildStageStatuses(Set<String> observedStages,
                                                                 List<Request> requests,
                                                                 String attemptView) {
        if (observedStages.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, RequestStatus> statuses = new LinkedHashMap<>();
        for (String stage : observedStages) {
            statuses.put(stage, requestStatusFor(requests, stage, attemptView));
        }
        return statuses;
    }

    private static RequestStatus requestStatusFor(List<Request> requests, String stage, String attemptView) {
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

    private static List<Request> stageRequestsByAttemptView(List<Request> requests, String stage, String attemptView) {
        List<Request> stageRequests = requests.stream()
                .filter(request -> stage.equals(request.getStage()))
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
        Map<String, Request> latestByStage = requests.stream()
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

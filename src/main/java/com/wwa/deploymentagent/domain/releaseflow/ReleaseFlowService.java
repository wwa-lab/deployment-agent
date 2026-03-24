package com.wwa.deploymentagent.domain.releaseflow;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.RequestArchiveResultDto;
import com.wwa.deploymentagent.contracts.dto.RequestPurgeResultDto;
import com.wwa.deploymentagent.contracts.dto.RequestRundownUpdateDto;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.ReviewStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.audit.AuditLoggerService;
import com.wwa.deploymentagent.domain.task.TaskRepository;
import com.wwa.deploymentagent.errors.NotFoundAppException;
import com.wwa.deploymentagent.errors.ValidationAppException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * ReleaseFlowService – Release Flow lifecycle management and aggregation.
 *
 * <p>Provides hierarchical state aggregation (task → request → stage → flow).
 * All state-changing operations are transactional.
 */
@Service
@RequiredArgsConstructor
public class ReleaseFlowService {

    private final ReleaseFlowRepository releaseFlowRepository;
    private final RequestRepository requestRepository;
    private final TaskRepository taskRepository;
    private final AuditLoggerService auditLogger;

    @PersistenceContext
    private EntityManager entityManager;

    /** Retrieve an active Release Flow by ID. Throws {@link NotFoundAppException} if absent. */
    @Transactional(readOnly = true)
    public ReleaseFlow getById(String id) {
        return releaseFlowRepository.findByIdAndArchivedAtIsNull(id)
                .orElseThrow(() -> new NotFoundAppException("ReleaseFlow", id));
    }

    /** Retrieve a Release Flow by ID, optionally including archived data for admin recovery flows. */
    @Transactional(readOnly = true)
    public ReleaseFlow getById(String id, boolean includeArchived) {
        if (includeArchived) {
            return releaseFlowRepository.findById(id)
                    .orElseThrow(() -> new NotFoundAppException("ReleaseFlow", id));
        }
        return getById(id);
    }

    /**
     * Load a Release Flow with its full request+task hierarchy in a single transaction.
     * Prefer this over {@link #getById} when requests and tasks will be accessed in service tests.
     */
    @Transactional
    public ReleaseFlow getByIdWithFullHierarchy(String id) {
        ReleaseFlow rf = getById(id);
        entityManager.flush();
        entityManager.refresh(rf);
        rf.getRequests().forEach(req -> {
            entityManager.refresh(req);
            req.getTasks().size();
        });
        return rf;
    }

    /** Paginated list with optional filters. Archived flows are hidden unless explicitly requested. */
    @Transactional(readOnly = true)
    public Page<ReleaseFlow> list(
            String projectId,
            FlowStatus flowStatus,
            Stage stage,
            Pageable pageable,
            boolean includeArchived) {
        return releaseFlowRepository.search(projectId, flowStatus, stage, includeArchived, pageable);
    }

    @Transactional(readOnly = true)
    public Page<ReleaseFlow> list(String projectId, FlowStatus flowStatus, Stage stage, Pageable pageable) {
        return list(projectId, flowStatus, stage, pageable, false);
    }

    @Transactional(readOnly = true)
    public Map<String, List<Request>> findRequestsByReleaseFlowIds(List<String> releaseFlowIds, boolean includeArchived) {
        if (releaseFlowIds == null || releaseFlowIds.isEmpty()) {
            return Map.of();
        }

        return requestRepository.findByReleaseFlowIds(releaseFlowIds, includeArchived).stream()
                .collect(Collectors.groupingBy(
                        request -> request.getReleaseFlow().getId(),
                        Collectors.collectingAndThen(Collectors.toList(), this::sortRequests)));
    }

    @Transactional(readOnly = true)
    public List<Request> findRequestsForFlow(String releaseFlowId, boolean includeArchived) {
        return sortRequests(requestRepository.findByReleaseFlowIdWithTasks(releaseFlowId, includeArchived));
    }

    /**
     * Find existing active Release Flow by grouping key (projectId, normalizedReleaseId).
     * Returns empty if not found.
     */
    @Transactional(readOnly = true)
    public Optional<ReleaseFlow> findByGroupKey(String projectId, String normalizedReleaseId) {
        return releaseFlowRepository.findByProjectIdAndNormalizedReleaseIdAndArchivedAtIsNull(
                projectId, normalizedReleaseId);
    }

    /**
     * Create a new Release Flow.
     * To be called inside a transaction during file import.
     */
    @Transactional
    public ReleaseFlow create(String projectId, String projectName,
                              String releaseId, String normalizedReleaseId, Stage firstStage) {
        ReleaseFlow rf = new ReleaseFlow();
        rf.setProjectId(projectId);
        rf.setProjectName(projectName);
        rf.setReleaseId(releaseId);
        rf.setNormalizedReleaseId(normalizedReleaseId);
        rf.setCurrentStage(firstStage);
        rf.setFlowStatus(FlowStatus.Pending);
        rf.setReviewStatus(ReviewStatus.Pending_Review);
        rf.setReviewOwner(null);
        rf.setArchivedAt(null);
        rf.setArchivedBy(null);
        return releaseFlowRepository.save(rf);
    }

    /**
     * Recompute and persist the Release Flow status from current visible child states.
     * Called after any state-changing operation.
     */
    @Transactional
    public void recomputeAndPersistStatus(String releaseFlowId) {
        ReleaseFlow rf = getById(releaseFlowId);
        List<Request> requests = requestRepository.findByReleaseFlowIdWithTasks(releaseFlowId, false);

        for (Request req : requests) {
            List<TaskStatus> taskStatuses = req.getTasks().stream()
                    .map(task -> task.getTaskStatus())
                    .toList();
            RequestStatus newStatus = ReleaseFlowAggregation.aggregateTasksToRequestStatus(taskStatuses);
            if (req.getRequestStatus() != newStatus) {
                req.setRequestStatus(newStatus);
                requestRepository.save(req);
            }
        }

        rf.setFlowStatus(aggregateFlowStatus(requests));
        rf.setReviewStatus(determineReviewStatus(requests));
        releaseFlowRepository.save(rf);
    }

    /** Advance the Release Flow's active stage to the next one in SIT→UAT→PROD order. */
    @Transactional
    public void advanceStage(String releaseFlowId) {
        ReleaseFlow rf = getById(releaseFlowId);
        Stage next = rf.getCurrentStage().next();
        if (next != null) {
            rf.setCurrentStage(next);
            rf.setFlowStatus(FlowStatus.Running);
            releaseFlowRepository.save(rf);
        }
    }

    @Transactional
    public Request updateRequestRundown(String releaseFlowId, String requestId, RequestRundownUpdateDto update) {
        Request request = requestRepository.findActiveByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));

        if (update.estimatedRemainingMinutes() != null && update.estimatedRemainingMinutes() < 0) {
            throw new ValidationAppException("Estimated remaining minutes must be zero or positive.");
        }

        request.setSnowGroup(normalizeBlank(update.snowGroup()));
        request.setApplication(normalizeBlank(update.application()));
        request.setSite(normalizeBlank(update.site()));
        request.setEstimatedRemainingMinutes(update.estimatedRemainingMinutes());
        Request saved = requestRepository.save(request);
        saved.getTasks().size();
        return saved;
    }

    @Transactional
    public Request startRequestDeployment(String releaseFlowId, String requestId, UserContext user) {
        Request request = requestRepository.findActiveByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));

        if (request.getRequestStatus() != RequestStatus.Pending) {
            throw new ValidationAppException("Only pending requests can be started.");
        }

        request.getTasks().stream()
                .filter(task -> task.getTaskStatus() == TaskStatus.Pending)
                .findFirst()
                .ifPresentOrElse(task -> {
                    task.setTaskStatus(TaskStatus.Ready_For_Execution);
                    taskRepository.save(task);
                }, () -> {
                    throw new ValidationAppException("Request has no pending tasks to start.");
                });

        recomputeAndPersistStatus(releaseFlowId);
        Request refreshed = requestRepository.findActiveByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        auditLogger.log(user, AuditActionType.request_start, releaseFlowId, requestId, null,
                Map.of("stage", refreshed.getStage().name()));
        return refreshed;
    }

    @Transactional
    public Request markRequestFailed(String releaseFlowId, String requestId, UserContext user) {
        Request request = requestRepository.findActiveByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));

        if (request.getRequestStatus() == RequestStatus.Completed
                || request.getRequestStatus() == RequestStatus.Failed
                || request.getRequestStatus() == RequestStatus.Rejected
                || request.getRequestStatus() == RequestStatus.Skipped) {
            throw new ValidationAppException("Only active requests can be marked as failed.");
        }

        boolean updatedAny = false;
        for (var task : request.getTasks()) {
            if (task.getTaskStatus() != TaskStatus.Approved
                    && task.getTaskStatus() != TaskStatus.Skipped
                    && task.getTaskStatus() != TaskStatus.Rejected
                    && task.getTaskStatus() != TaskStatus.Failed) {
                task.setTaskStatus(TaskStatus.Failed);
                taskRepository.save(task);
                updatedAny = true;
            }
        }

        if (!updatedAny) {
            throw new ValidationAppException("Request has no active tasks that can be failed.");
        }

        recomputeAndPersistStatus(releaseFlowId);
        Request refreshed = requestRepository.findActiveByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        auditLogger.log(user, AuditActionType.request_fail, releaseFlowId, requestId, null,
                Map.of("stage", refreshed.getStage().name()));
        return refreshed;
    }

    @Transactional
    public RequestArchiveResultDto archiveRequestRundown(String releaseFlowId, String requestId, UserContext user) {
        Request request = requestRepository.findActiveByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        ReleaseFlow releaseFlow = getById(releaseFlowId);

        Instant archivedAt = Instant.now();
        request.setArchivedAt(archivedAt);
        request.setArchivedBy(user.userId());
        requestRepository.save(request);
        entityManager.flush();

        List<Request> activeRequests = requestRepository.findByReleaseFlowIdWithTasks(releaseFlowId, false);
        boolean releaseFlowArchived = activeRequests.isEmpty();

        if (releaseFlowArchived) {
            archiveReleaseFlow(releaseFlow, archivedAt, user.userId());
        } else {
            clearReleaseFlowArchive(releaseFlow);
            syncReleaseFlowAfterVisibleRequestChange(releaseFlow, activeRequests);
        }
        releaseFlowRepository.save(releaseFlow);

        auditLogger.log(user, AuditActionType.request_archive, releaseFlowId, requestId, null,
                Map.of(
                        "stage", request.getStage().name(),
                        "releaseFlowArchived", releaseFlowArchived,
                        "activeRequestCount", activeRequests.size()));

        return new RequestArchiveResultDto(
                releaseFlowId,
                requestId,
                request.getStage(),
                true,
                releaseFlowArchived,
                activeRequests.size());
    }

    @Transactional
    public RequestArchiveResultDto restoreRequestRundown(String releaseFlowId, String requestId, UserContext user) {
        Request request = requestRepository.findByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        if (request.getArchivedAt() == null) {
            throw new ValidationAppException("Only archived rundowns can be restored.");
        }

        ReleaseFlow releaseFlow = getById(releaseFlowId, true);
        request.setArchivedAt(null);
        request.setArchivedBy(null);
        requestRepository.save(request);
        entityManager.flush();

        clearReleaseFlowArchive(releaseFlow);
        List<Request> activeRequests = requestRepository.findByReleaseFlowIdWithTasks(releaseFlowId, false);
        syncReleaseFlowAfterVisibleRequestChange(releaseFlow, activeRequests);
        releaseFlowRepository.save(releaseFlow);

        auditLogger.log(user, AuditActionType.request_restore, releaseFlowId, requestId, null,
                Map.of(
                        "stage", request.getStage().name(),
                        "activeRequestCount", activeRequests.size()));

        return new RequestArchiveResultDto(
                releaseFlowId,
                requestId,
                request.getStage(),
                false,
                false,
                activeRequests.size());
    }

    @Transactional
    public RequestPurgeResultDto purgeArchivedRequestRundown(String releaseFlowId, String requestId, UserContext user) {
        Request request = requestRepository.findByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        if (request.getArchivedAt() == null) {
            throw new ValidationAppException("Only archived rundowns can be permanently deleted.");
        }

        Stage stage = request.getStage();
        int requestCountBeforeDelete = requestRepository.findByReleaseFlowIdWithTasks(releaseFlowId, true).size();
        boolean releaseFlowDeleted = requestCountBeforeDelete <= 1;
        requestRepository.delete(request);
        entityManager.flush();
        entityManager.clear();

        if (releaseFlowDeleted) {
            releaseFlowRepository.deleteById(releaseFlowId);
        } else {
            List<Request> remainingRequests = requestRepository.findByReleaseFlowIdWithTasks(releaseFlowId, true);
            List<Request> activeRequests = remainingRequests.stream()
                    .filter(remaining -> remaining.getArchivedAt() == null)
                    .toList();
            ReleaseFlow releaseFlow = getById(releaseFlowId, true);
            if (activeRequests.isEmpty()) {
                Instant archivedAt = releaseFlow.getArchivedAt() != null ? releaseFlow.getArchivedAt() : Instant.now();
                String archivedBy = releaseFlow.getArchivedBy() != null ? releaseFlow.getArchivedBy() : user.userId();
                archiveReleaseFlow(releaseFlow, archivedAt, archivedBy);
            } else {
                clearReleaseFlowArchive(releaseFlow);
                syncReleaseFlowAfterVisibleRequestChange(releaseFlow, activeRequests);
            }
            releaseFlowRepository.save(releaseFlow);

            auditLogger.log(user, AuditActionType.request_purge, releaseFlowId, requestId, null,
                    Map.of(
                            "stage", stage.name(),
                            "releaseFlowDeleted", false,
                            "remainingRequestCount", remainingRequests.size(),
                            "activeRequestCount", activeRequests.size()));

            return new RequestPurgeResultDto(
                    releaseFlowId,
                    requestId,
                    stage,
                    false,
                    remainingRequests.size(),
                    activeRequests.size());
        }

        auditLogger.log(user, AuditActionType.request_purge, releaseFlowId, requestId, null,
                Map.of(
                        "stage", stage.name(),
                        "releaseFlowDeleted", true,
                        "remainingRequestCount", 0,
                        "activeRequestCount", 0));

        return new RequestPurgeResultDto(
                releaseFlowId,
                requestId,
                stage,
                true,
                0,
                0);
    }

    private void syncReleaseFlowAfterVisibleRequestChange(ReleaseFlow releaseFlow, List<Request> visibleRequests) {
        if (visibleRequests.isEmpty()) {
            return;
        }

        releaseFlow.setCurrentStage(determineCurrentStage(visibleRequests, releaseFlow.getCurrentStage()));
        releaseFlow.setFlowStatus(aggregateFlowStatus(visibleRequests));
        releaseFlow.setReviewStatus(determineReviewStatus(visibleRequests));
        releaseFlow.setReviewOwner(null);
    }

    private void archiveReleaseFlow(ReleaseFlow releaseFlow, Instant archivedAt, String archivedBy) {
        releaseFlow.setArchivedAt(archivedAt);
        releaseFlow.setArchivedBy(archivedBy);
        releaseFlow.setReviewOwner(null);
    }

    private void clearReleaseFlowArchive(ReleaseFlow releaseFlow) {
        releaseFlow.setArchivedAt(null);
        releaseFlow.setArchivedBy(null);
    }

    private FlowStatus aggregateFlowStatus(List<Request> requests) {
        List<RequestStatus> stageStatuses = java.util.Arrays.stream(Stage.values())
                .flatMap(stage -> {
                    List<RequestStatus> stageReqs = requests.stream()
                            .filter(request -> request.getStage() == stage)
                            .map(Request::getRequestStatus)
                            .toList();
                    if (stageReqs.isEmpty()) {
                        return java.util.stream.Stream.empty();
                    }
                    return java.util.stream.Stream.of(
                            ReleaseFlowAggregation.aggregateRequestsToStageStatus(stageReqs));
                })
                .toList();
        return ReleaseFlowAggregation.aggregateStagesToFlowStatus(stageStatuses);
    }

    private Stage determineCurrentStage(List<Request> requests, Stage currentStage) {
        if (requests.stream().anyMatch(request -> request.getStage() == currentStage)) {
            return currentStage;
        }

        List<Request> sortedRequests = sortRequests(requests);
        return sortedRequests.stream()
                .filter(request -> request.getRequestStatus() != RequestStatus.Completed
                        && request.getRequestStatus() != RequestStatus.Skipped)
                .map(Request::getStage)
                .findFirst()
                .orElse(sortedRequests.get(sortedRequests.size() - 1).getStage());
    }

    private ReviewStatus determineReviewStatus(List<Request> requests) {
        if (requests.stream().anyMatch(request -> request.getRequestStatus() == RequestStatus.Rejected)) {
            return ReviewStatus.Rejected;
        }

        if (!requests.isEmpty() && requests.stream().allMatch(request ->
                request.getRequestStatus() == RequestStatus.Completed
                        || request.getRequestStatus() == RequestStatus.Skipped)) {
            return ReviewStatus.Approved;
        }

        return ReviewStatus.Pending_Review;
    }

    private List<Request> sortRequests(List<Request> requests) {
        return requests.stream()
                .sorted(Comparator
                        .comparing(Request::getStage)
                        .thenComparing(request -> request.getArchivedAt() != null)
                        .thenComparing(Request::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

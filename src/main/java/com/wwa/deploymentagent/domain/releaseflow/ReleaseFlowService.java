package com.wwa.deploymentagent.domain.releaseflow;

import com.wwa.deploymentagent.contracts.UserContext;
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

    /** Retrieve a Release Flow by ID. Throws {@link NotFoundAppException} if absent. */
    @Transactional(readOnly = true)
    public ReleaseFlow getById(String id) {
        return releaseFlowRepository.findById(id)
                .orElseThrow(() -> new NotFoundAppException("ReleaseFlow", id));
    }

    /**
     * Load a Release Flow with its full request+task hierarchy in a single query.
     * Prefer this over {@link #getById} when requests and tasks will be accessed,
     * to avoid N+1 queries.
     */
    @Transactional
    public ReleaseFlow getByIdWithFullHierarchy(String id) {
        ReleaseFlow rf = releaseFlowRepository.findById(id)
                .orElseThrow(() -> new NotFoundAppException("ReleaseFlow", id));
        // Flush and refresh to ensure all in-session changes are visible in the returned hierarchy.
        // Collections initialized as empty during persist() would otherwise miss newly added children.
        entityManager.flush();
        entityManager.refresh(rf);
        rf.getRequests().forEach(req -> {
            entityManager.refresh(req);
            req.getTasks().size();
        });
        return rf;
    }

    /** Paginated list with optional filters. Callers build the Pageable from query params. */
    @Transactional(readOnly = true)
    public Page<ReleaseFlow> list(String projectId, FlowStatus flowStatus, Stage stage, Pageable pageable) {
        boolean hasProject = projectId != null;
        boolean hasStatus  = flowStatus != null;
        boolean hasStage   = stage != null;

        if (hasProject && hasStatus && hasStage)
            return releaseFlowRepository.findByProjectIdAndFlowStatusAndCurrentStage(projectId, flowStatus, stage, pageable);
        if (hasProject && hasStatus)
            return releaseFlowRepository.findByProjectIdAndFlowStatus(projectId, flowStatus, pageable);
        if (hasProject && hasStage)
            return releaseFlowRepository.findByProjectIdAndCurrentStage(projectId, stage, pageable);
        if (hasStatus && hasStage)
            return releaseFlowRepository.findByFlowStatusAndCurrentStage(flowStatus, stage, pageable);
        if (hasProject)
            return releaseFlowRepository.findByProjectId(projectId, pageable);
        if (hasStatus)
            return releaseFlowRepository.findByFlowStatus(flowStatus, pageable);
        if (hasStage)
            return releaseFlowRepository.findByCurrentStage(stage, pageable);
        return releaseFlowRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Map<String, List<Request>> findRequestsByReleaseFlowIds(List<String> releaseFlowIds) {
        if (releaseFlowIds == null || releaseFlowIds.isEmpty()) {
            return Map.of();
        }

        return requestRepository.findByReleaseFlowIds(releaseFlowIds).stream()
                .collect(Collectors.groupingBy(request -> request.getReleaseFlow().getId()));
    }

    /**
     * Find existing Release Flow by grouping key (projectId, normalizedReleaseId).
     * Returns empty if not found.
     */
    @Transactional(readOnly = true)
    public Optional<ReleaseFlow> findByGroupKey(String projectId, String normalizedReleaseId) {
        return releaseFlowRepository.findByProjectIdAndNormalizedReleaseId(projectId, normalizedReleaseId);
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
        return releaseFlowRepository.save(rf);
    }

    /**
     * Recompute and persist the Release Flow status from current child states.
     * Reads all Requests and their Tasks, aggregates bottom-up, then updates the flow.
     * Called after any state-changing operation (callback, decision, progression).
     */
    @Transactional
    public void recomputeAndPersistStatus(String releaseFlowId) {
        ReleaseFlow rf = getById(releaseFlowId);
        List<Request> requests = requestRepository.findByReleaseFlowIdWithTasks(releaseFlowId);

        // Aggregate task → request status for each request
        for (Request req : requests) {
            List<TaskStatus> taskStatuses = req.getTasks().stream()
                    .map(t -> t.getTaskStatus())
                    .toList();
            RequestStatus newStatus = ReleaseFlowAggregation.aggregateTasksToRequestStatus(taskStatuses);
            if (req.getRequestStatus() != newStatus) {
                req.setRequestStatus(newStatus);
                requestRepository.save(req);
            }
        }

        // Aggregate request → stage → flow status.
        // Only include stages that have at least one Request.
        List<RequestStatus> stageStatuses = java.util.Arrays.stream(Stage.values())
                .flatMap(stage -> {
                    List<RequestStatus> stageReqs = requests.stream()
                            .filter(r -> r.getStage() == stage)
                            .map(Request::getRequestStatus)
                            .toList();
                    if (stageReqs.isEmpty()) return java.util.stream.Stream.empty();
                    return java.util.stream.Stream.of(
                            ReleaseFlowAggregation.aggregateRequestsToStageStatus(stageReqs));
                })
                .toList();

        FlowStatus newFlowStatus = ReleaseFlowAggregation.aggregateStagesToFlowStatus(stageStatuses);
        if (rf.getFlowStatus() != newFlowStatus) {
            rf.setFlowStatus(newFlowStatus);
            releaseFlowRepository.save(rf);
        }
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
        Request request = requestRepository.findByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
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
        Request request = requestRepository.findByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
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
        Request refreshed = requestRepository.findByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        auditLogger.log(user, AuditActionType.request_start, releaseFlowId, requestId, null,
                java.util.Map.of("stage", refreshed.getStage().name()));
        return refreshed;
    }

    @Transactional
    public Request markRequestFailed(String releaseFlowId, String requestId, UserContext user) {
        Request request = requestRepository.findByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
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
        Request refreshed = requestRepository.findByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        auditLogger.log(user, AuditActionType.request_fail, releaseFlowId, requestId, null,
                java.util.Map.of("stage", refreshed.getStage().name()));
        return refreshed;
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

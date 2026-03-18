package com.wwa.deploymentagent.domain.releaseflow;

import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.RequestStatus;
import com.wwa.deploymentagent.contracts.enums.ReviewStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.errors.NotFoundAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

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
    @Transactional(readOnly = true)
    public ReleaseFlow getByIdWithFullHierarchy(String id) {
        return releaseFlowRepository.findByIdWithFullHierarchy(id)
                .orElseThrow(() -> new NotFoundAppException("ReleaseFlow", id));
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
}

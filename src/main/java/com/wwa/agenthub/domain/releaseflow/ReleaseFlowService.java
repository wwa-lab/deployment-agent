package com.wwa.agenthub.domain.releaseflow;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.RequestArchiveResultDto;
import com.wwa.agenthub.contracts.dto.ReleaseFlowDetailDto;
import com.wwa.agenthub.contracts.dto.ReleaseFlowListItemDto;
import com.wwa.agenthub.contracts.dto.RequestPurgeResultDto;
import com.wwa.agenthub.contracts.dto.RequestRundownUpdateDto;
import com.wwa.agenthub.contracts.dto.RequestDto;
import com.wwa.agenthub.contracts.dto.TaskDto;
import com.wwa.agenthub.contracts.enums.AuditActionType;
import com.wwa.agenthub.contracts.enums.FlowStatus;
import com.wwa.agenthub.contracts.enums.RequestStatus;
import com.wwa.agenthub.contracts.enums.ReviewStatus;
import com.wwa.agenthub.contracts.enums.TaskStatus;
import com.wwa.agenthub.agents.deployment.domain.ReleaseFlowFamilyKey;
import com.wwa.agenthub.platform.domain.StagePipeline;
import com.wwa.agenthub.platform.domain.StagePipelineRegistry;
import com.wwa.agenthub.domain.audit.AuditLoggerService;
import com.wwa.agenthub.domain.task.TaskRepository;
import com.wwa.agenthub.domain.task.Task;
import com.wwa.agenthub.errors.ForbiddenAppException;
import com.wwa.agenthub.errors.ConflictAppException;
import com.wwa.agenthub.errors.NotFoundAppException;
import com.wwa.agenthub.errors.ValidationAppException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.time.Clock;
import java.time.Instant;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Objects;
import java.util.Set;
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
    public static final String ATTEMPT_VIEW_LATEST = ReleaseFlowListItemDto.ATTEMPT_VIEW_LATEST;
    public static final String ATTEMPT_VIEW_HISTORY = ReleaseFlowListItemDto.ATTEMPT_VIEW_HISTORY;

    private final ReleaseFlowRepository releaseFlowRepository;
    private final RequestRepository requestRepository;
    private final TaskRepository taskRepository;
    private final AuditLoggerService auditLogger;
    private final StagePipelineRegistry stagePipelineRegistry;
    private final Clock clock;

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
            String stage,
            String application,
            String snowGroup,
            String agent,
            UserContext user,
            Pageable pageable,
            boolean includeArchived) {
        String normalizedApplication = normalizeBlank(application);
        String normalizedSnowGroup = normalizeBlank(snowGroup);
        String normalizedAgent = normalizeBlank(agent);
        boolean scopeRestricted = user != null && !user.isGlobalDevOpsAdmin();

        if (!scopeRestricted
                && normalizedApplication == null
                && normalizedSnowGroup == null
                && normalizedAgent == null) {
            return releaseFlowRepository.search(projectId, flowStatus, stage, includeArchived, pageable);
        }

        Page<ReleaseFlow> basePage = releaseFlowRepository.search(
                projectId,
                flowStatus,
                stage,
                includeArchived,
                Pageable.unpaged());
        List<ReleaseFlow> baseFlows = basePage.getContent();
        Map<String, List<Request>> requestsByReleaseFlowId = findRequestsByReleaseFlowIds(
                baseFlows.stream().map(ReleaseFlow::getId).toList(),
                includeArchived);

        List<ReleaseFlow> filtered = baseFlows.stream()
                .filter(releaseFlow -> matchesScope(
                        releaseFlow,
                        requestsByReleaseFlowId.getOrDefault(releaseFlow.getId(), List.of()),
                        normalizedApplication,
                        normalizedSnowGroup,
                        normalizedAgent))
                .filter(releaseFlow -> matchesUserScope(
                        user,
                        requestsByReleaseFlowId.getOrDefault(releaseFlow.getId(), List.of())))
                .toList();

        if (pageable.isUnpaged()) {
            return new PageImpl<>(filtered, pageable, filtered.size());
        }

        int fromIndex = Math.min((int) pageable.getOffset(), filtered.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), filtered.size());
        List<ReleaseFlow> pageContent = filtered.subList(fromIndex, toIndex);

        return new PageImpl<>(pageContent, pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public Page<ReleaseFlow> list(String projectId, FlowStatus flowStatus, String stage, Pageable pageable) {
        return list(projectId, flowStatus, stage, null, null, null, null, pageable, false);
    }

    @Transactional(readOnly = true)
    public Page<ReleaseFlow> list(String projectId,
                                  FlowStatus flowStatus,
                                  String stage,
                                  String application,
                                  String snowGroup,
                                  String agent,
                                  Pageable pageable,
                                  boolean includeArchived) {
        return list(projectId, flowStatus, stage, application, snowGroup, agent, null, pageable, includeArchived);
    }

    /**
     * Agent-scoped list query introduced in BA-T12 (Build Agent refactor PL-5).
     *
     * <p>Unlike {@link #list}, this method filters by the {@code request.agent} column
     * rather than by free-text search on the agent column. Only release flows with at
     * least one non-archived request owned by {@code agentId} are returned.
     *
     * <p>Rows with {@code agent IS NULL} are excluded (post-V13 backfill there should
     * be none; the filter defends against future null writes).
     */
    @Transactional(readOnly = true)
    public Page<ReleaseFlow> listByAgent(String agentId, ReleaseFlowFilter filter, Pageable pageable) {
        if (agentId == null || agentId.isBlank()) {
            throw new ValidationAppException("agentId is required for listByAgent");
        }
        ReleaseFlowFilter effectiveFilter = filter == null ? ReleaseFlowFilter.empty() : filter;

        Page<ReleaseFlow> basePage = list(
                effectiveFilter.projectId(),
                effectiveFilter.flowStatus(),
                effectiveFilter.stage(),
                effectiveFilter.application(),
                effectiveFilter.snowGroup(),
                null,
                effectiveFilter.user(),
                Pageable.unpaged(),
                effectiveFilter.includeArchived());

        List<ReleaseFlow> baseFlows = basePage.getContent();
        Map<String, List<Request>> requestsByReleaseFlowId = findRequestsByReleaseFlowIds(
                baseFlows.stream().map(ReleaseFlow::getId).toList(),
                effectiveFilter.includeArchived());

        List<ReleaseFlow> filtered = baseFlows.stream()
                .filter(releaseFlow -> requestsByReleaseFlowId
                        .getOrDefault(releaseFlow.getId(), List.of())
                        .stream()
                        .anyMatch(request -> agentId.equals(request.getAgent())))
                .toList();

        if (pageable.isUnpaged()) {
            return new PageImpl<>(filtered, pageable, filtered.size());
        }

        int fromIndex = Math.min((int) pageable.getOffset(), filtered.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), filtered.size());
        return new PageImpl<>(filtered.subList(fromIndex, toIndex), pageable, filtered.size());
    }

    @Transactional(readOnly = true)
    public Page<ReleaseFlowListItemDto> listStitchedSummaries(String projectId,
                                                              FlowStatus flowStatus,
                                                              String stage,
                                                              String application,
                                                              String snowGroup,
                                                              String agent,
                                                              UserContext user,
                                                              String attemptView,
                                                              Pageable pageable,
                                                              boolean includeArchived) {
        String normalizedAttemptView = normalizeAttemptView(attemptView);
        Page<ReleaseFlow> baseResult = list(
                projectId,
                null,
                null,
                application,
                snowGroup,
                agent,
                user,
                Pageable.unpaged(),
                includeArchived);

        List<ReleaseFlow> baseFlows = baseResult.getContent();
        Map<String, List<Request>> requestsByReleaseFlowId = findRequestsByReleaseFlowIds(
                baseFlows.stream().map(ReleaseFlow::getId).toList(),
                includeArchived);

        List<StitchedSummaryCandidate> stitchedSummaries = baseFlows.stream()
                .collect(Collectors.groupingBy(
                        this::stitchedGroupKey,
                        LinkedHashMap::new,
                        Collectors.toList()))
                .values()
                .stream()
                .map(groupedFlows -> buildStitchedSummary(groupedFlows, requestsByReleaseFlowId, normalizedAttemptView))
                .filter(candidate -> flowStatus == null || candidate.dto().flowStatus() == flowStatus)
                .filter(candidate -> stage == null || stage.equals(candidate.dto().currentStage()))
                .sorted(Comparator
                        .comparing(StitchedSummaryCandidate::sortTimestamp, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparingInt((StitchedSummaryCandidate candidate) -> -stageOrdinal(candidate.dto().currentStage())))
                .toList();

        int fromIndex = Math.min((int) pageable.getOffset(), stitchedSummaries.size());
        int toIndex = Math.min(fromIndex + pageable.getPageSize(), stitchedSummaries.size());
        List<ReleaseFlowListItemDto> pageContent = stitchedSummaries.subList(fromIndex, toIndex).stream()
                .map(StitchedSummaryCandidate::dto)
                .toList();

        return new PageImpl<>(pageContent, pageable, stitchedSummaries.size());
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

    @Transactional(readOnly = true)
    public ReleaseFlowDetailDto getStitchedDetail(String releaseFlowId,
                                                  List<String> linkedFlowIds,
                                                  boolean includeArchived,
                                                  UserContext user) {
        List<String> requestedIds = normalizeLinkedFlowIds(releaseFlowId, linkedFlowIds);
        List<ReleaseFlow> releaseFlows = requestedIds.stream()
                .map(id -> getById(id, includeArchived))
                .toList();
        validateStitchedDetailFamily(releaseFlows);

        Map<String, List<Request>> requestsByReleaseFlowId = findRequestsByReleaseFlowIds(requestedIds, includeArchived);
        ReleaseFlow representativeFlow = representativeFlow(releaseFlows, requestsByReleaseFlowId);
        List<Request> stitchedRequests = sortRequests(releaseFlows.stream()
                .flatMap(releaseFlow -> requestsByReleaseFlowId
                        .getOrDefault(releaseFlow.getId(), List.of())
                        .stream())
                .toList());
        List<Request> visibleRequests = filterVisibleRequests(stitchedRequests, user);
        if (visibleRequests.isEmpty()) {
            throw new ForbiddenAppException("view_release_flow");
        }

        List<String> linkedReleaseIds = orderedDistinctReleaseIds(releaseFlows, requestsByReleaseFlowId, representativeFlow);
        List<RequestDto> requestDtos = visibleRequests.stream()
                .map(req -> RequestDto.from(req, req.getTasks().stream()
                        .filter(task -> !task.isIntegrationBound())
                        .map(TaskDto::from)
                        .toList()))
                .toList();

        return new ReleaseFlowDetailDto(
                representativeFlow.getId(),
                representativeFlow.getProjectId(),
                representativeFlow.getProjectName(),
                representativeFlow.getReleaseId(),
                ReleaseFlowFamilyKey.fromStoredRelease(
                        representativeFlow.getReleaseId(),
                        representativeFlow.getNormalizedReleaseId()),
                deriveCurrentStage(stitchedRequests, representativeFlow.getCurrentStage()),
                aggregateFlowStatus(stitchedRequests),
                determineReviewStatus(stitchedRequests),
                archivedAtFor(releaseFlows),
                archivedByFor(releaseFlows, representativeFlow),
                linkedReleaseIds.size() > 1,
                linkedReleaseIds.size(),
                linkedReleaseIds,
                requestDtos);
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
                              String releaseId, String normalizedReleaseId, String firstStage) {
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

    /** Advance the Release Flow's active stage to the next one using the agent's stage pipeline. */
    @Transactional
    public void advanceStage(String releaseFlowId) {
        ReleaseFlow rf = getById(releaseFlowId);
        List<Request> requests = requestRepository.findByReleaseFlowIdAndArchivedAtIsNull(releaseFlowId);
        String agentId = requests.stream()
                .map(Request::getAgent)
                .filter(agent -> agent != null && !agent.isBlank())
                .findFirst()
                .orElse(null);
        StagePipeline pipeline = stagePipelineRegistry.forAgent(agentId);
        pipeline.next(rf.getCurrentStage()).ifPresent(next -> {
            rf.setCurrentStage(next);
            rf.setFlowStatus(FlowStatus.Running);
            releaseFlowRepository.save(rf);
        });
    }

    @Transactional
    public Request updateRequestRundown(String releaseFlowId, String requestId, RequestRundownUpdateDto update) {
        Request request = requestRepository.findActiveByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        List<Task> lockedTasks = lockAndRefreshRequestTasks(requestId);
        entityManager.refresh(request);

        if (update == null) {
            throw new ValidationAppException("Rundown update is required.");
        }

        if (update.estimatedRemainingMinutes() != null && update.estimatedRemainingMinutes() < 0) {
            throw new ValidationAppException("Estimated remaining minutes must be zero or positive.");
        }

        String targetSnowGroup = update.snowGroup() == null
                ? request.getSnowGroup() : normalizeBlank(update.snowGroup());
        String targetApplication = update.application() == null
                ? request.getApplication() : normalizeBlank(update.application());
        String targetAgent = update.agent() == null
                ? request.getAgent() : normalizeBlank(update.agent());
        boolean integrationBound = lockedTasks.stream().anyMatch(Task::isIntegrationBound);
        if (integrationBound
                && (!Objects.equals(targetSnowGroup, request.getSnowGroup())
                || !Objects.equals(targetApplication, request.getApplication())
                || !Objects.equals(targetAgent, request.getAgent()))) {
            throw new ConflictAppException(
                    "Application, team, and Agent cannot change after Atlas Integration binding");
        }

        request.setSnowGroup(targetSnowGroup);
        request.setApplication(targetApplication);
        request.setAgent(targetAgent);
        request.setOwner(update.owner() == null ? request.getOwner() : normalizeBlank(update.owner()));
        request.setSite(update.site() == null ? request.getSite() : normalizeBlank(update.site()));
        request.setEstimatedRemainingMinutes(update.estimatedRemainingMinutes() == null
                ? request.getEstimatedRemainingMinutes()
                : update.estimatedRemainingMinutes());
        Request saved = requestRepository.save(request);
        saved.getTasks().size();
        return saved;
    }

    @Transactional
    public Request startRequestDeployment(String releaseFlowId, String requestId, UserContext user) {
        Request request = requestRepository.findActiveByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        List<Task> lockedTasks = lockAndRefreshRequestTasks(requestId);
        entityManager.refresh(request);

        if (request.getRequestStatus() != RequestStatus.Pending) {
            throw new ValidationAppException("Only pending requests can be started.");
        }

        lockedTasks.stream()
                .sorted(REQUEST_TASK_ORDER)
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
                Map.of("stage", refreshed.getStage()));
        return refreshed;
    }

    @Transactional
    public Request markRequestFailed(String releaseFlowId, String requestId, UserContext user) {
        Request request = requestRepository.findActiveByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));

        if (request.getTasks().stream().anyMatch(task -> task.isIntegrationBound()
                && task.getTaskStatus() != TaskStatus.Approved
                && task.getTaskStatus() != TaskStatus.Rejected
                && task.getTaskStatus() != TaskStatus.Skipped
                && task.getTaskStatus() != TaskStatus.Failed)) {
            throw new ConflictAppException(
                    "Integration-bound Tasks must reach a terminal state through Atlas Execution or Review commands");
        }

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
                Map.of("stage", refreshed.getStage()));
        return refreshed;
    }

    @Transactional
    public RequestArchiveResultDto archiveRequestRundown(String releaseFlowId, String requestId, UserContext user) {
        Request request = requestRepository.findActiveByIdAndReleaseFlowIdWithTasks(requestId, releaseFlowId)
                .orElseThrow(() -> new NotFoundAppException("Request", requestId));
        List<Task> lockedTasks = lockAndRefreshRequestTasks(requestId);
        entityManager.refresh(request);
        boolean hasActiveIntegrationExecution = lockedTasks.stream()
                .anyMatch(task -> task.isIntegrationBound() && task.getActiveExecutionId() != null);
        if (hasActiveIntegrationExecution) {
            throw new ConflictAppException(
                    "A Request with an active Atlas Integration Execution cannot be archived");
        }
        ReleaseFlow releaseFlow = getById(releaseFlowId);

        Instant archivedAt = clock.instant();
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
                        "stage", request.getStage(),
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

    private static final Comparator<Task> REQUEST_TASK_ORDER = Comparator
            .comparing(Task::getTaskGroupId, Comparator.nullsFirst(String::compareTo))
            .thenComparing(Task::getStepSeq, Comparator.nullsFirst(Integer::compareTo))
            .thenComparing(Task::getId);

    private List<Task> lockAndRefreshRequestTasks(String requestId) {
        List<String> taskIds = taskRepository
                .findByRequestIdOrderByTaskGroupIdAscStepSeqAsc(requestId)
                .stream()
                .map(Task::getId)
                .sorted()
                .toList();
        return taskIds.stream()
                .map(taskId -> taskRepository.findByIdForExecutionUpdate(taskId)
                        .orElseThrow(() -> new NotFoundAppException("Task", taskId)))
                .peek(entityManager::refresh)
                .toList();
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
                        "stage", request.getStage(),
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
        if (request.getTasks().stream().anyMatch(Task::isIntegrationBound)) {
            throw new ConflictAppException(
                    "Atlas Integration audit and execution evidence is retention-protected and cannot be purged");
        }

        String stage = request.getStage();
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
                Instant archivedAt = releaseFlow.getArchivedAt() != null ? releaseFlow.getArchivedAt() : clock.instant();
                String archivedBy = releaseFlow.getArchivedBy() != null ? releaseFlow.getArchivedBy() : user.userId();
                archiveReleaseFlow(releaseFlow, archivedAt, archivedBy);
            } else {
                clearReleaseFlowArchive(releaseFlow);
                syncReleaseFlowAfterVisibleRequestChange(releaseFlow, activeRequests);
            }
            releaseFlowRepository.save(releaseFlow);

            auditLogger.log(user, AuditActionType.request_purge, releaseFlowId, requestId, null,
                    Map.of(
                            "stage", stage,
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
                        "stage", stage,
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
        return aggregateFlowStatus(requests, ATTEMPT_VIEW_LATEST);
    }

    private FlowStatus aggregateFlowStatus(List<Request> requests, String attemptView) {
        String normalizedAttemptView = normalizeAttemptView(attemptView);
        List<RequestStatus> stageStatuses = allKnownStages(requests).stream()
                .flatMap(stage -> {
                    List<RequestStatus> stageReqs = requestsForStage(requests, stage, normalizedAttemptView).stream()
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

    private String determineCurrentStage(List<Request> requests, String currentStage) {
        if (requests.stream().anyMatch(request -> currentStage.equals(request.getStage()))) {
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
        return determineReviewStatus(requests, ATTEMPT_VIEW_LATEST);
    }

    private ReviewStatus determineReviewStatus(List<Request> requests, String attemptView) {
        List<Request> viewedRequests = requestsByAttemptView(requests, attemptView);

        if (viewedRequests.stream().anyMatch(request -> request.getRequestStatus() == RequestStatus.Rejected)) {
            return ReviewStatus.Rejected;
        }

        if (!viewedRequests.isEmpty() && viewedRequests.stream().allMatch(request ->
                request.getRequestStatus() == RequestStatus.Completed
                        || request.getRequestStatus() == RequestStatus.Skipped)) {
            return ReviewStatus.Approved;
        }

        return ReviewStatus.Pending_Review;
    }

    private List<Request> sortRequests(List<Request> requests) {
        return requests.stream()
                .sorted(Comparator
                        .comparingInt((Request r) -> stageOrdinal(r.getStage()))
                        .thenComparing(request -> request.getArchivedAt() != null)
                        .thenComparing(Request::getAttemptNumber, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Request::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private StitchedSummaryCandidate buildStitchedSummary(List<ReleaseFlow> groupedFlows,
                                                          Map<String, List<Request>> requestsByReleaseFlowId,
                                                          String attemptView) {
        ReleaseFlow representativeFlow = representativeFlow(groupedFlows, requestsByReleaseFlowId);
        List<Request> stitchedRequests = sortRequests(groupedFlows.stream()
                .flatMap(releaseFlow -> requestsByReleaseFlowId
                        .getOrDefault(releaseFlow.getId(), List.of())
                        .stream())
                .toList());
        String currentStage = deriveCurrentStage(stitchedRequests, representativeFlow.getCurrentStage());
        List<String> linkedReleaseIds = orderedDistinctReleaseIds(groupedFlows, requestsByReleaseFlowId, representativeFlow);
        List<String> linkedReleaseFlowIds = orderedDistinctFlowIds(groupedFlows, requestsByReleaseFlowId, representativeFlow);
        Request scopeRequest = scopeRequestForCurrentStage(stitchedRequests, currentStage);

        Set<String> observedStages = observedStages(stitchedRequests);
        Map<String, RequestStatus> stageStatuses = new LinkedHashMap<>();
        for (String stage : observedStages) {
            stageStatuses.put(stage, stageStatusFor(stitchedRequests, stage, attemptView));
        }

        ReleaseFlowListItemDto dto = new ReleaseFlowListItemDto(
                representativeFlow.getId(),
                representativeFlow.getProjectId(),
                representativeFlow.getProjectName(),
                representativeFlow.getReleaseId(),
                ReleaseFlowFamilyKey.fromStoredRelease(
                        representativeFlow.getReleaseId(),
                        representativeFlow.getNormalizedReleaseId()),
                currentStage,
                aggregateFlowStatus(stitchedRequests, attemptView),
                determineReviewStatus(stitchedRequests, attemptView),
                archivedAtFor(groupedFlows),
                archivedByFor(groupedFlows, representativeFlow),
                scopeRequest != null && scopeRequest.getApplication() != null
                        ? scopeRequest.getApplication()
                        : representativeFlow.getProjectName(),
                scopeRequest != null ? scopeRequest.getSnowGroup() : null,
                scopeRequest != null ? scopeRequest.getAgent() : null,
                scopeRequest != null ? scopeRequest.getOwner() : null,
                stageStatuses,
                observedStages,
                groupedFlows.size() > 1 || linkedReleaseIds.size() > 1,
                linkedReleaseIds.size(),
                linkedReleaseIds,
                linkedReleaseFlowIds);

        return new StitchedSummaryCandidate(
                representativeFlow.getUpdatedAt(),
                dto);
    }

    private ReleaseFlow representativeFlow(List<ReleaseFlow> releaseFlows,
                                           Map<String, List<Request>> requestsByReleaseFlowId) {
        return releaseFlows.stream()
                .max(Comparator
                        .comparingInt((ReleaseFlow releaseFlow) ->
                                stageOrdinal(highestPresentStage(
                                        requestsByReleaseFlowId.getOrDefault(releaseFlow.getId(), List.of()),
                                        releaseFlow.getCurrentStage())))
                        .thenComparing(ReleaseFlow::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(ReleaseFlow::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())))
                .orElseThrow(() -> new ValidationAppException("Unable to determine a representative release flow."));
    }

    private static final List<String> STAGE_ORDER = List.of("SIT", "UAT", "PROD");

    private static int stageOrdinal(String stage) {
        int idx = STAGE_ORDER.indexOf(stage);
        return idx >= 0 ? idx : Integer.MAX_VALUE;
    }

    private String highestPresentStage(List<Request> requests, String fallbackStage) {
        return requests.stream()
                .map(Request::getStage)
                .max(Comparator.comparingInt(ReleaseFlowService::stageOrdinal))
                .orElse(fallbackStage);
    }

    private String deriveCurrentStage(List<Request> requests, String fallbackStage) {
        return highestPresentStage(requests, fallbackStage);
    }

    private Request scopeRequestForCurrentStage(List<Request> requests, String currentStage) {
        return requests.stream()
                .filter(request -> request.getArchivedAt() == null)
                .filter(request -> currentStage.equals(request.getStage()))
                .max(requestAttemptComparator())
                .or(() -> requests.stream()
                        .filter(request -> request.getArchivedAt() == null)
                        .max(requestAttemptComparator()))
                .orElse(requests.isEmpty() ? null : requests.get(0));
    }

    private RequestStatus stageStatusFor(List<Request> requests, String stage, String attemptView) {
        List<RequestStatus> stageStatuses = requestsForStage(requests, stage, attemptView).stream()
                .map(Request::getRequestStatus)
                .toList();
        return ReleaseFlowAggregation.aggregateRequestsToStageStatus(stageStatuses);
    }

    private Set<String> observedStages(List<Request> requests) {
        if (requests == null || requests.isEmpty()) {
            return Collections.emptySet();
        }
        return requests.stream()
                .map(Request::getStage)
                .filter(stage -> stage != null && !stage.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private List<Request> requestsByAttemptView(List<Request> requests, String attemptView) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        if (ATTEMPT_VIEW_HISTORY.equalsIgnoreCase(normalizeAttemptView(attemptView))) {
            return requests;
        }

        Map<String, Request> latestByStage = requests.stream()
                .collect(Collectors.toMap(
                        Request::getStage,
                        request -> request,
                        (left, right) -> requestAttemptComparator().compare(left, right) >= 0 ? left : right));

        return latestByStage.values().stream().toList();
    }

    private List<Request> requestsForStage(List<Request> requests, String stage, String attemptView) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        List<Request> stageRequests = requests.stream()
                .filter(request -> stage.equals(request.getStage()))
                .toList();
        if (stageRequests.isEmpty()) {
            return List.of();
        }

        if (ATTEMPT_VIEW_HISTORY.equalsIgnoreCase(normalizeAttemptView(attemptView))) {
            return stageRequests;
        }

        return stageRequests.stream()
                .max(requestAttemptComparator())
                .map(List::of)
                .orElse(List.of());
    }

    private Comparator<Request> requestAttemptComparator() {
        return Comparator
                .comparing(Request::getAttemptNumber, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(Request::getUpdatedAt, Comparator.nullsLast(Comparator.naturalOrder()));
    }

    private Instant archivedAtFor(List<ReleaseFlow> releaseFlows) {
        if (releaseFlows.stream().anyMatch(releaseFlow -> releaseFlow.getArchivedAt() == null)) {
            return null;
        }

        return releaseFlows.stream()
                .map(ReleaseFlow::getArchivedAt)
                .filter(archivedAt -> archivedAt != null)
                .max(Comparator.naturalOrder())
                .orElse(null);
    }

    private String archivedByFor(List<ReleaseFlow> releaseFlows, ReleaseFlow representativeFlow) {
        return archivedAtFor(releaseFlows) == null ? null : representativeFlow.getArchivedBy();
    }

    private List<String> orderedDistinctReleaseIds(List<ReleaseFlow> releaseFlows,
                                                   Map<String, List<Request>> requestsByReleaseFlowId,
                                                   ReleaseFlow representativeFlow) {
        return orderedReleaseFlows(releaseFlows, requestsByReleaseFlowId, representativeFlow).stream()
                .map(ReleaseFlow::getReleaseId)
                .filter(releaseId -> releaseId != null && !releaseId.isBlank())
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new));
    }

    private List<String> orderedDistinctFlowIds(List<ReleaseFlow> releaseFlows,
                                                Map<String, List<Request>> requestsByReleaseFlowId,
                                                ReleaseFlow representativeFlow) {
        return orderedReleaseFlows(releaseFlows, requestsByReleaseFlowId, representativeFlow).stream()
                .map(ReleaseFlow::getId)
                .collect(Collectors.collectingAndThen(
                        Collectors.toCollection(LinkedHashSet::new),
                        ArrayList::new));
    }

    private List<ReleaseFlow> orderedReleaseFlows(List<ReleaseFlow> releaseFlows,
                                                  Map<String, List<Request>> requestsByReleaseFlowId,
                                                  ReleaseFlow representativeFlow) {
        return releaseFlows.stream()
                .sorted(Comparator
                        .comparing((ReleaseFlow releaseFlow) -> !releaseFlow.getId().equals(representativeFlow.getId()))
                        .thenComparingInt((ReleaseFlow releaseFlow) ->
                                -stageOrdinal(highestPresentStage(
                                        requestsByReleaseFlowId.getOrDefault(releaseFlow.getId(), List.of()),
                                        releaseFlow.getCurrentStage())))
                        .thenComparing(ReleaseFlow::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(ReleaseFlow::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    private String stitchedGroupKey(ReleaseFlow releaseFlow) {
        return releaseFlow.getProjectId()
                + "::"
                + ReleaseFlowFamilyKey.fromStoredRelease(
                        releaseFlow.getReleaseId(),
                        releaseFlow.getNormalizedReleaseId());
    }

    private List<String> normalizeLinkedFlowIds(String releaseFlowId, List<String> linkedFlowIds) {
        Set<String> orderedIds = new LinkedHashSet<>();
        orderedIds.add(releaseFlowId);
        if (linkedFlowIds != null) {
            linkedFlowIds.stream()
                    .filter(value -> value != null && !value.isBlank())
                    .map(String::trim)
                    .forEach(orderedIds::add);
        }
        return orderedIds.stream().toList();
    }

    private void validateStitchedDetailFamily(List<ReleaseFlow> releaseFlows) {
        if (releaseFlows.isEmpty()) {
            throw new NotFoundAppException("ReleaseFlow", "stitched");
        }

        String projectId = releaseFlows.get(0).getProjectId();
        String familyKey = stitchedGroupKey(releaseFlows.get(0));
        boolean sameFamily = releaseFlows.stream().allMatch(releaseFlow ->
                projectId.equals(releaseFlow.getProjectId()) && familyKey.equals(stitchedGroupKey(releaseFlow)));
        if (!sameFamily) {
            throw new ValidationAppException("Linked release flows must belong to the same project release family.");
        }
    }

    private List<Request> filterVisibleRequests(List<Request> requests, UserContext user) {
        if (user == null || user.isGlobalDevOpsAdmin()) {
            return requests;
        }
        return requests.stream()
                .filter(request -> user.hasScopedAccess(request.getApplication(), request.getSnowGroup()))
                .toList();
    }

    private String normalizeAttemptView(String attemptView) {
        if (attemptView == null || attemptView.isBlank()) {
            return ATTEMPT_VIEW_LATEST;
        }
        return attemptView.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private boolean matchesScope(
            ReleaseFlow releaseFlow,
            List<Request> requests,
            String application,
            String snowGroup,
            String agent) {
        if (requests == null || requests.isEmpty()) {
            return matchesContains(releaseFlow.getProjectName(), application)
                    && snowGroup == null
                    && agent == null;
        }

        return requests.stream().anyMatch(request ->
                matchesContains(request.getApplication() != null ? request.getApplication() : releaseFlow.getProjectName(), application)
                        && matchesContains(request.getSnowGroup(), snowGroup)
                        && matchesContains(request.getAgent(), agent));
    }

    private boolean matchesContains(String actualValue, String expectedFragment) {
        if (expectedFragment == null) {
            return true;
        }
        if (actualValue == null) {
            return false;
        }
        return actualValue.toLowerCase().contains(expectedFragment.toLowerCase());
    }

    private boolean matchesUserScope(UserContext user, List<Request> requests) {
        if (user == null || user.isGlobalDevOpsAdmin()) {
            return true;
        }
        if (requests == null || requests.isEmpty()) {
            return false;
        }
        return requests.stream()
                .anyMatch(request -> user.hasScopedAccess(request.getApplication(), request.getSnowGroup()));
    }

    /** Collect distinct stages present in the given requests, preserving natural order. */
    private List<String> allKnownStages(List<Request> requests) {
        return requests.stream()
                .map(Request::getStage)
                .distinct()
                .sorted()
                .toList();
    }

    private record StitchedSummaryCandidate(
            Instant sortTimestamp,
            ReleaseFlowListItemDto dto
    ) {}
}

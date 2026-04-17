package com.wwa.agenthub.agents.project.web;

import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.*;
import com.wwa.agenthub.contracts.enums.FlowStatus;
import com.wwa.agenthub.domain.fileimport.ImportResult;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlow;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlowFilter;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlowService;
import com.wwa.agenthub.domain.releaseflow.Request;
import com.wwa.agenthub.domain.releaseflow.TemplateRundownCreationService;
import com.wwa.agenthub.errors.ForbiddenAppException;
import com.wwa.agenthub.errors.ValidationAppException;
import com.wwa.agenthub.platform.web.security.AgentBoundaryGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/project-agent/release-flows")
@RequiredArgsConstructor
public class ProjectReleaseFlowController {
    private static final String INITIAL_STAGE = "REQUIREMENT";

    private final ReleaseFlowService releaseFlowService;
    private final TemplateRundownCreationService templateRundownCreationService;
    private final AgentBoundaryGuard boundaryGuard;

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<ReleaseFlowListItemDto>> list(
            @RequestParam(required = false) String project,
            @RequestParam(required = false) FlowStatus status,
            @RequestParam(required = false) String stage,
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String snowGroup,
            @RequestParam(required = false) String agent,
            @RequestParam(defaultValue = "latest") String attemptView,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserContext user) {

        if (page < 0) throw new ValidationAppException("Invalid page parameter", page);
        if (size < 1) throw new ValidationAppException("Invalid size parameter", size);
        if (size > 100) throw new ValidationAppException("Page size cannot exceed 100", size);
        validateArchivedViewer(includeArchived, user);
        validateAttemptView(attemptView);
        validateStage(stage);

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        ReleaseFlowFilter filter = new ReleaseFlowFilter(
                project, status, normalizeStage(stage), application, snowGroup, user, includeArchived);
        Page<ReleaseFlow> result = releaseFlowService.listByAgent(AgentId.PROJECT_AGENT, filter, pageable);
        Map<String, List<Request>> requestsByReleaseFlowId = releaseFlowService.findRequestsByReleaseFlowIds(
                result.getContent().stream().map(ReleaseFlow::getId).toList(),
                includeArchived);

        List<ReleaseFlowListItemDto> dtos = result.getContent().stream()
                .map(releaseFlow -> {
                    List<Request> requests = requestsByReleaseFlowId.getOrDefault(releaseFlow.getId(), List.of());
                    String currentStage = releaseFlowService.resolveCurrentStage(requests, releaseFlow.getCurrentStage());
                    Map<String, com.wwa.agenthub.contracts.enums.RequestStatus> stageStatuses =
                            releaseFlowService.resolveStageStatuses(requests, attemptView);
                    java.util.Set<String> stagesPresent = releaseFlowService.resolveObservedStages(requests);
                    Request scopeRequest = requests.stream()
                            .filter(request -> request.getArchivedAt() == null)
                            .max(java.util.Comparator
                                    .comparing(Request::getAttemptNumber, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                                    .thenComparing(Request::getUpdatedAt, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                            .orElse(requests.isEmpty() ? null : requests.get(0));

                    return new ReleaseFlowListItemDto(
                            releaseFlow.getId(),
                            releaseFlow.getProjectId(),
                            releaseFlow.getProjectName(),
                            releaseFlow.getReleaseId(),
                            releaseFlow.getNormalizedReleaseId(),
                            currentStage,
                            releaseFlow.getFlowStatus(),
                            releaseFlow.getReviewStatus(),
                            releaseFlow.getArchivedAt(),
                            releaseFlow.getArchivedBy(),
                            scopeRequest != null && scopeRequest.getApplication() != null
                                    ? scopeRequest.getApplication()
                                    : releaseFlow.getProjectName(),
                            scopeRequest != null ? scopeRequest.getSnowGroup() : null,
                            scopeRequest != null ? scopeRequest.getAgent() : null,
                            scopeRequest != null ? scopeRequest.getOwner() : null,
                            stageStatuses,
                            stagesPresent,
                            false,
                            1,
                            List.of(releaseFlow.getReleaseId()),
                            List.of(releaseFlow.getId()));
                })
                .toList();

        return ResponseEntity.ok(new PaginatedResponseDto<>(
                dtos, result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReleaseFlowDetailDto> getById(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @AuthenticationPrincipal UserContext user) {
        validateArchivedViewer(includeArchived, user);
        boundaryGuard.assertFlowBelongsToAgent(id, AgentId.PROJECT_AGENT);

        ReleaseFlow rf = releaseFlowService.getById(id, includeArchived);
        List<Request> visibleRequests = filterVisibleRequests(
                releaseFlowService.findRequestsForFlow(id, includeArchived),
                user);
        if (visibleRequests.isEmpty()) {
            throw new ForbiddenAppException("view_release_flow");
        }

        List<RequestDto> requestDtos = visibleRequests.stream()
                .map(req -> RequestDto.from(req, req.getTasks().stream().map(TaskDto::from).toList()))
                .toList();

        return ResponseEntity.ok(new ReleaseFlowDetailDto(
                rf.getId(), rf.getProjectId(), rf.getProjectName(),
                rf.getReleaseId(), rf.getNormalizedReleaseId(),
                releaseFlowService.resolveCurrentStage(visibleRequests, rf.getCurrentStage()),
                rf.getFlowStatus(), rf.getReviewStatus(),
                rf.getArchivedAt(), rf.getArchivedBy(),
                false,
                1,
                List.of(rf.getReleaseId()),
                requestDtos));
    }

    @PostMapping("/from-template")
    public ResponseEntity<UploadResponseDto> createFromTemplate(
            @RequestBody CreateRundownFromTemplateDto body,
            @AuthenticationPrincipal UserContext user) {
        validateRundownEditor(user);
        CreateRundownFromTemplateDto forced = new CreateRundownFromTemplateDto(
                body.templateId(), body.templateName(),
                body.projectId(), body.projectName(),
                INITIAL_STAGE, body.releaseId(),
                body.snowGroup(), body.application(),
                AgentId.PROJECT_AGENT, body.site(),
                body.owner(), body.estimatedRemainingMinutes(),
                body.tasks());
        ImportResult result = templateRundownCreationService.createRundown(forced, user);
        return ResponseEntity.ok(UploadResponseDto.from(result));
    }

    @PatchMapping("/{flowId}/requests/{requestId}/rundown")
    public ResponseEntity<RequestDto> updateRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @RequestBody RequestRundownUpdateDto body,
            @AuthenticationPrincipal UserContext user) {
        validateRundownEditor(user);
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.PROJECT_AGENT);
        Request requestForValidation = findRequestForScopeValidation(flowId, requestId, false);
        validateRequestScope(user, requestForValidation, "update_rundown");
        validateOwnerEdit(user, body);

        Request request = releaseFlowService.updateRequestRundown(flowId, requestId, body);
        List<TaskDto> taskDtos = request.getTasks().stream()
                .map(TaskDto::from)
                .toList();
        return ResponseEntity.ok(RequestDto.from(request, taskDtos));
    }

    @PostMapping("/{flowId}/requests/{requestId}/archive")
    public ResponseEntity<RequestArchiveResultDto> archiveRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        validateRundownEditor(user);
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.PROJECT_AGENT);
        validateRequestScope(user, findRequestForScopeValidation(flowId, requestId, false), "archive_rundown");
        return ResponseEntity.ok(releaseFlowService.archiveRequestRundown(flowId, requestId, user));
    }

    @PostMapping("/{flowId}/requests/{requestId}/restore")
    public ResponseEntity<RequestArchiveResultDto> restoreRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "restore_rundown");
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.PROJECT_AGENT);
        validateRequestScope(user, findRequestForScopeValidation(flowId, requestId, true), "restore_rundown");
        return ResponseEntity.ok(releaseFlowService.restoreRequestRundown(flowId, requestId, user));
    }

    @DeleteMapping("/{flowId}/requests/{requestId}/purge")
    public ResponseEntity<RequestPurgeResultDto> purgeArchivedRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "purge_rundown");
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.PROJECT_AGENT);
        validateRequestScope(user, findRequestForScopeValidation(flowId, requestId, true), "purge_rundown");
        return ResponseEntity.ok(releaseFlowService.purgeArchivedRequestRundown(flowId, requestId, user));
    }

    @PostMapping("/{flowId}/requests/{requestId}/start")
    public ResponseEntity<RequestDto> startRequestDeployment(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.PROJECT_AGENT);
        Request requestForValidation = findRequestForScopeValidation(flowId, requestId, false);
        validateRequestScope(user, requestForValidation, "start_deployment");
        validateRundownOperator(user, requestForValidation, "start_deployment");

        Request request = releaseFlowService.startRequestDeployment(flowId, requestId, user);
        List<TaskDto> taskDtos = request.getTasks().stream()
                .map(TaskDto::from)
                .toList();
        return ResponseEntity.ok(RequestDto.from(request, taskDtos));
    }

    @PostMapping("/{flowId}/requests/{requestId}/fail")
    public ResponseEntity<RequestDto> markRequestFailed(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.PROJECT_AGENT);
        Request requestForValidation = findRequestForScopeValidation(flowId, requestId, false);
        validateRequestScope(user, requestForValidation, "mark_request_failed");
        validateRundownOperator(user, requestForValidation, "mark_request_failed");

        Request request = releaseFlowService.markRequestFailed(flowId, requestId, user);
        List<TaskDto> taskDtos = request.getTasks().stream()
                .map(TaskDto::from)
                .toList();
        return ResponseEntity.ok(RequestDto.from(request, taskDtos));
    }

    private void validateArchivedViewer(boolean includeArchived, UserContext user) {
        if (!includeArchived) return;
        if (user == null || !user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException("view_archived_rundown");
        }
    }

    private void validateAttemptView(String attemptView) {
        if ("latest".equalsIgnoreCase(attemptView) || "history".equalsIgnoreCase(attemptView)) {
            return;
        }
        throw new ValidationAppException(
                "Invalid attemptView parameter: '" + attemptView + "'. Must be latest or history.");
    }

    private void validateStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return;
        }
        normalizeRequiredStage(stage);
    }

    private String normalizeStage(String stage) {
        if (stage == null || stage.isBlank()) {
            return stage;
        }
        return normalizeRequiredStage(stage);
    }

    private String normalizeRequiredStage(String stage) {
        if (stage == null || stage.isBlank()) {
            throw new ValidationAppException("stage is required");
        }
        try {
            return com.wwa.agenthub.agents.project.domain.ProjectStage
                    .fromString(stage.trim().toUpperCase())
                    .name();
        } catch (IllegalArgumentException ex) {
            throw new ValidationAppException(
                    "Invalid stage: '" + stage + "'. Must be one of REQUIREMENT, FUNCTIONAL_DESIGN, "
                            + "TECHNICAL_DESIGN, DEVELOPMENT, TESTING, PERFORMANCE_TEST, RESULT_SIGNOFF, "
                            + "BUSINESS_ENDORSEMENT, CAB, DEPLOYMENT, POST_IMPLEMENTATION.");
        }
    }

    private List<Request> filterVisibleRequests(List<Request> requests, UserContext user) {
        if (user == null || user.isGlobalDevOpsAdmin()) {
            return requests;
        }
        return requests.stream()
                .filter(req -> user.hasScopedAccess(req.getApplication(), req.getSnowGroup()))
                .toList();
    }

    private Request findRequestForScopeValidation(String flowId, String requestId, boolean includeArchived) {
        return releaseFlowService.findRequestsForFlow(flowId, includeArchived).stream()
                .filter(request -> request.getId().equals(requestId))
                .findFirst()
                .orElseThrow(() -> new ForbiddenAppException("request_scope_lookup"));
    }

    private void validateRundownEditor(UserContext user) {
        if (user == null) {
            throw new ForbiddenAppException("update_rundown");
        }
        if (!user.hasRole("DEVELOPER") && !user.hasRole("TL") && !user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException("update_rundown");
        }
    }

    private void validateAdmin(UserContext user, String permission) {
        if (user == null || !user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException(permission);
        }
    }

    private void validateRundownOperator(UserContext user, Request request, String permission) {
        if (user == null) {
            throw new ForbiddenAppException(permission);
        }
        if (user.hasRole("DEVOPS_ADMIN")) {
            return;
        }
        if ((!user.hasRole("DEVELOPER") && !user.hasRole("TL")) || !isRundownOwner(user, request)) {
            throw new ForbiddenAppException(permission);
        }
    }

    private void validateRequestScope(UserContext user, Request request, String permission) {
        if (user == null) {
            throw new ForbiddenAppException(permission);
        }
        if (user.isGlobalDevOpsAdmin()) {
            return;
        }
        if (!user.hasScopedAccess(request.getApplication(), request.getSnowGroup())) {
            throw new ForbiddenAppException(permission);
        }
    }

    private void validateOwnerEdit(UserContext user, RequestRundownUpdateDto body) {
        if (body == null || body.owner() == null || body.owner().isBlank()) {
            return;
        }
        if (user == null || (!user.hasRole("TL") && !user.hasRole("DEVOPS_ADMIN"))) {
            throw new ForbiddenAppException("update_rundown_owner");
        }
    }

    private boolean isRundownOwner(UserContext user, Request request) {
        String owner = normalizeIdentity(request.getOwner());
        if (owner == null) {
            return false;
        }

        String displayName = user.displayName() == null ? null : user.displayName().replaceAll("\\s*\\(.*\\)$", "").trim();
        String firstName = displayName == null || displayName.isBlank() ? null : displayName.split("\\s+")[0];

        return List.of(user.userId(), displayName, firstName)
                .stream()
                .map(this::normalizeIdentity)
                .filter(value -> value != null)
                .anyMatch(owner::equals);
    }

    private String normalizeIdentity(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.toLowerCase().replaceAll("[^a-z0-9]", "");
    }
}

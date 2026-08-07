package com.wwa.agenthub.agents.build.web;

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

/**
 * Build Agent release flow controller (BA-T21).
 *
 * <pre>
 *   GET  /api/build-agent/release-flows           – DEV-stage list scoped to build-agent
 *   GET  /api/build-agent/release-flows/:id      – single flow detail (no stitching)
 *   POST /api/build-agent/release-flows/from-template – create from template (forces DEV + build-agent)
 * </pre>
 *
 * <p>PL-6 boundary: every endpoint forces {@code agent = "build-agent"} server-side.
 * No stitching — `?linked=` is ignored on getById (BA-3).
 */
@RestController
@RequestMapping("/api/build-agent/release-flows")
@RequiredArgsConstructor
public class BuildReleaseFlowController {

    private static final String FORCED_STAGE = "DEV";

    private final ReleaseFlowService releaseFlowService;
    private final TemplateRundownCreationService templateRundownCreationService;
    private final AgentBoundaryGuard boundaryGuard;

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<ReleaseFlowListItemDto>> list(
            @RequestParam(required = false) String project,
            @RequestParam(required = false) FlowStatus status,
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String snowGroup,
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

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        ReleaseFlowFilter filter = new ReleaseFlowFilter(
                project, status, "DEV", application, snowGroup, user, includeArchived);
        Page<ReleaseFlow> result = releaseFlowService.listByAgent(AgentId.BUILD_AGENT, filter, pageable);
        Map<String, List<Request>> requestsByReleaseFlowId = releaseFlowService.findRequestsByReleaseFlowIds(
                result.getContent().stream().map(ReleaseFlow::getId).toList(),
                includeArchived);

        List<ReleaseFlowListItemDto> dtos = result.getContent().stream()
                .map(rf -> ReleaseFlowListItemDto.from(
                        rf,
                        requestsByReleaseFlowId.getOrDefault(rf.getId(), List.of()),
                        attemptView))
                .toList();

        return ResponseEntity.ok(new PaginatedResponseDto<>(
                dtos, result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    @PostMapping("/{flowId}/requests/{requestId}/start")
    public ResponseEntity<RequestDto> startRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.BUILD_AGENT);
        Request requestForValidation = findRequestForScopeValidation(flowId, requestId);
        validateRequestScope(user, requestForValidation, "start_rundown");
        validateRundownOperator(user, requestForValidation, "start_rundown");

        Request request = releaseFlowService.startRequestDeployment(flowId, requestId, user);
        List<TaskDto> taskDtos = request.getTasks().stream()
                .filter(task -> !task.isIntegrationBound())
                .map(TaskDto::from)
                .toList();
        return ResponseEntity.ok(RequestDto.from(request, taskDtos));
    }

    @PostMapping("/{flowId}/requests/{requestId}/fail")
    public ResponseEntity<RequestDto> markRequestFailed(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.BUILD_AGENT);
        Request requestForValidation = findRequestForScopeValidation(flowId, requestId);
        validateRequestScope(user, requestForValidation, "mark_request_failed");
        validateRundownOperator(user, requestForValidation, "mark_request_failed");

        Request request = releaseFlowService.markRequestFailed(flowId, requestId, user);
        List<TaskDto> taskDtos = request.getTasks().stream()
                .filter(task -> !task.isIntegrationBound())
                .map(TaskDto::from)
                .toList();
        return ResponseEntity.ok(RequestDto.from(request, taskDtos));
    }

    @PatchMapping("/{flowId}/requests/{requestId}/rundown")
    public ResponseEntity<RequestDto> updateRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @RequestBody RequestRundownUpdateDto body,
            @AuthenticationPrincipal UserContext user) {
        validateRundownEditor(user);
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.BUILD_AGENT);
        Request requestForValidation = findRequestForScopeValidation(flowId, requestId);
        validateRequestScope(user, requestForValidation, "update_rundown");
        validateRundownDestination(user, requestForValidation, body, AgentId.BUILD_AGENT);
        validateOwnerEdit(user, body);

        Request request = releaseFlowService.updateRequestRundown(flowId, requestId, body);
        List<TaskDto> taskDtos = request.getTasks().stream()
                .filter(task -> !task.isIntegrationBound())
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
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.BUILD_AGENT);
        validateRequestScope(user, findRequestForScopeValidation(flowId, requestId), "archive_rundown");
        return ResponseEntity.ok(releaseFlowService.archiveRequestRundown(flowId, requestId, user));
    }

    @PostMapping("/{flowId}/requests/{requestId}/restore")
    public ResponseEntity<RequestArchiveResultDto> restoreRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "restore_rundown");
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.BUILD_AGENT);
        validateRequestScope(user, findRequestForScopeValidation(flowId, requestId, true), "restore_rundown");
        return ResponseEntity.ok(releaseFlowService.restoreRequestRundown(flowId, requestId, user));
    }

    @DeleteMapping("/{flowId}/requests/{requestId}/purge")
    public ResponseEntity<RequestPurgeResultDto> purgeArchivedRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "purge_rundown");
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.BUILD_AGENT);
        validateRequestScope(user, findRequestForScopeValidation(flowId, requestId, true), "purge_rundown");
        return ResponseEntity.ok(releaseFlowService.purgeArchivedRequestRundown(flowId, requestId, user));
    }

    @PostMapping("/from-template")
    public ResponseEntity<UploadResponseDto> createFromTemplate(
            @RequestBody CreateRundownFromTemplateDto body,
            @AuthenticationPrincipal UserContext user) {
        validateRundownEditor(user);
        CreateRundownFromTemplateDto forced = new CreateRundownFromTemplateDto(
                body.templateId(), body.templateName(),
                body.projectId(), body.projectName(),
                FORCED_STAGE, body.releaseId(),
                body.snowGroup(), body.application(),
                AgentId.BUILD_AGENT, body.site(),
                body.owner(), body.estimatedRemainingMinutes(),
                body.tasks());
        ImportResult result = templateRundownCreationService.createRundown(forced, user);
        return ResponseEntity.ok(UploadResponseDto.from(result));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReleaseFlowDetailDto> getById(
            @PathVariable String id,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @AuthenticationPrincipal UserContext user) {
        validateArchivedViewer(includeArchived, user);
        boundaryGuard.assertFlowBelongsToAgent(id, AgentId.BUILD_AGENT);

        ReleaseFlow rf = releaseFlowService.getById(id, includeArchived);
        List<Request> visibleRequests = filterVisibleRequests(
                releaseFlowService.findRequestsForFlow(id, includeArchived),
                user);
        if (visibleRequests.isEmpty()) {
            throw new ForbiddenAppException("view_release_flow");
        }

        List<RequestDto> requestDtos = visibleRequests.stream()
                .map(req -> RequestDto.from(req, req.getTasks().stream()
                        .filter(task -> !task.isIntegrationBound())
                        .map(TaskDto::from)
                        .toList()))
                .toList();

        return ResponseEntity.ok(new ReleaseFlowDetailDto(
                rf.getId(), rf.getProjectId(), rf.getProjectName(),
                rf.getReleaseId(), rf.getNormalizedReleaseId(),
                rf.getCurrentStage(), rf.getFlowStatus(), rf.getReviewStatus(),
                rf.getArchivedAt(), rf.getArchivedBy(),
                false,
                1,
                List.of(rf.getReleaseId()),
                requestDtos));
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

    private List<Request> filterVisibleRequests(List<Request> requests, UserContext user) {
        if (user == null || user.isGlobalDevOpsAdmin()) {
            return requests;
        }
        return requests.stream()
                .filter(req -> user.hasScopedAccess(req.getApplication(), req.getSnowGroup()))
                .toList();
    }

    private Request findRequestForScopeValidation(String flowId, String requestId) {
        return findRequestForScopeValidation(flowId, requestId, false);
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

    private void validateAdmin(UserContext user, String action) {
        if (user == null || !user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException(action);
        }
    }

    private void validateOwnerEdit(UserContext user, RequestRundownUpdateDto body) {
        if (body == null) {
            return;
        }
        if (body.owner() != null && (user == null || !user.hasRole("DEVOPS_ADMIN"))) {
            throw new ForbiddenAppException("update_rundown_owner");
        }
    }

    private void validateRequestScope(UserContext user, Request request, String action) {
        if (user == null) {
            throw new ForbiddenAppException(action);
        }
        if (user.isGlobalDevOpsAdmin()) {
            return;
        }
        if (!user.hasScopedAccess(request.getApplication(), request.getSnowGroup())) {
            throw new ForbiddenAppException(action);
        }
    }

    private void validateRundownDestination(
            UserContext user,
            Request current,
            RequestRundownUpdateDto body,
            String expectedAgent
    ) {
        if (body == null) {
            throw new ValidationAppException("Rundown update is required.");
        }
        String targetAgent = body.agent() == null ? current.getAgent() : body.agent().trim();
        String targetApplication = body.application() == null
                ? current.getApplication() : body.application().trim();
        String targetSnowGroup = body.snowGroup() == null
                ? current.getSnowGroup() : body.snowGroup().trim();
        if (!expectedAgent.equals(targetAgent)
                || (user != null && !user.isGlobalDevOpsAdmin()
                && !user.hasScopedAccess(targetApplication, targetSnowGroup))) {
            throw new ForbiddenAppException("update_rundown_destination");
        }
    }

    private void validateRundownOperator(UserContext user, Request request, String action) {
        if (user == null) {
            throw new ForbiddenAppException(action);
        }
        if (user.hasRole("DEVOPS_ADMIN")) {
            return;
        }
        if ((!user.hasRole("DEVELOPER") && !user.hasRole("TL")) || !isRundownOwner(user, request)) {
            throw new ForbiddenAppException(action);
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

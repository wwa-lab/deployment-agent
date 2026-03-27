package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.dto.*;
import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowService;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import com.wwa.deploymentagent.errors.ValidationAppException;
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
 * Release Flow controller.
 *
 * <pre>
 *   GET /api/deployment-agent/release-flows           – paginated list with optional filters
 *   GET /api/deployment-agent/release-flows/:id       – detail with nested requests and tasks
 * </pre>
 */
@RestController
@RequestMapping("/api/deployment-agent/release-flows")
@RequiredArgsConstructor
public class ReleaseFlowController {

    private final ReleaseFlowService releaseFlowService;

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<ReleaseFlowListItemDto>> list(
            @RequestParam(required = false) String project,
            @RequestParam(required = false) FlowStatus status,
            @RequestParam(required = false) Stage stage,
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String snowGroup,
            @RequestParam(required = false) String agent,
            @RequestParam(defaultValue = "flow") String view,
            @RequestParam(defaultValue = "latest") String attemptView,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserContext user) {

        if (page < 0) throw new ValidationAppException("Invalid page parameter", page);
        if (size < 1) throw new ValidationAppException("Invalid size parameter", size);
        if (size > 100) throw new ValidationAppException("Page size cannot exceed 100", size);
        validateArchivedViewer(includeArchived, user);
        validateViewMode(view);
        validateAttemptView(attemptView);

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        if ("stitched".equalsIgnoreCase(view)) {
            Page<ReleaseFlowListItemDto> stitchedResult = releaseFlowService.listStitchedSummaries(
                    project, status, stage, application, snowGroup, agent, user, attemptView, pageable, includeArchived);
            return ResponseEntity.ok(new PaginatedResponseDto<>(
                    stitchedResult.getContent(),
                    stitchedResult.getTotalElements(),
                    stitchedResult.getNumber(),
                    stitchedResult.getSize()));
        }

        Page<ReleaseFlow> result = releaseFlowService.list(
                project, status, stage, application, snowGroup, agent, user, pageable, includeArchived);
        Map<String, List<Request>> requestsByReleaseFlowId = releaseFlowService.findRequestsByReleaseFlowIds(
                result.getContent().stream().map(ReleaseFlow::getId).toList(),
                includeArchived);

        List<ReleaseFlowListItemDto> dtos = result.getContent().stream()
                .map(releaseFlow -> ReleaseFlowListItemDto.from(
                        releaseFlow,
                        requestsByReleaseFlowId.getOrDefault(releaseFlow.getId(), List.of()),
                        attemptView))
                .toList();

        return ResponseEntity.ok(new PaginatedResponseDto<>(
                dtos, result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReleaseFlowDetailDto> getById(
            @PathVariable String id,
            @RequestParam(required = false) String linked,
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @AuthenticationPrincipal UserContext user) {
        validateArchivedViewer(includeArchived, user);
        List<String> linkedFlowIds = parseLinkedFlowIds(linked);
        if (!linkedFlowIds.isEmpty()) {
            return ResponseEntity.ok(releaseFlowService.getStitchedDetail(id, linkedFlowIds, includeArchived, user));
        }

        ReleaseFlow rf = releaseFlowService.getById(id, includeArchived);
        List<Request> visibleRequests = filterVisibleRequests(
                releaseFlowService.findRequestsForFlow(id, includeArchived),
                user);
        if (visibleRequests.isEmpty()) {
            throw new ForbiddenAppException("view_release_flow");
        }

        List<RequestDto> requestDtos = visibleRequests.stream()
                .map(req -> {
                    List<TaskDto> taskDtos = req.getTasks().stream()
                            .map(TaskDto::from)
                            .toList();
                    return RequestDto.from(req, taskDtos);
                })
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

    @PatchMapping("/{flowId}/requests/{requestId}/rundown")
    public ResponseEntity<RequestDto> updateRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @RequestBody RequestRundownUpdateDto body,
            @AuthenticationPrincipal UserContext user) {
        validateRundownEditor(user);
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
        validateRequestScope(
                user,
                findRequestForScopeValidation(flowId, requestId, false),
                "archive_rundown");
        return ResponseEntity.ok(releaseFlowService.archiveRequestRundown(flowId, requestId, user));
    }

    @PostMapping("/{flowId}/requests/{requestId}/restore")
    public ResponseEntity<RequestArchiveResultDto> restoreRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "restore_rundown");
        validateRequestScope(
                user,
                findRequestForScopeValidation(flowId, requestId, true),
                "restore_rundown");
        return ResponseEntity.ok(releaseFlowService.restoreRequestRundown(flowId, requestId, user));
    }

    @DeleteMapping("/{flowId}/requests/{requestId}/purge")
    public ResponseEntity<RequestPurgeResultDto> purgeArchivedRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "purge_rundown");
        validateRequestScope(
                user,
                findRequestForScopeValidation(flowId, requestId, true),
                "purge_rundown");
        return ResponseEntity.ok(releaseFlowService.purgeArchivedRequestRundown(flowId, requestId, user));
    }

    @PostMapping("/{flowId}/requests/{requestId}/start")
    public ResponseEntity<RequestDto> startRequestDeployment(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
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
        Request requestForValidation = findRequestForScopeValidation(flowId, requestId, false);
        validateRequestScope(user, requestForValidation, "mark_request_failed");
        validateRundownOperator(user, requestForValidation, "mark_request_failed");

        Request request = releaseFlowService.markRequestFailed(flowId, requestId, user);
        List<TaskDto> taskDtos = request.getTasks().stream()
                .map(TaskDto::from)
                .toList();
        return ResponseEntity.ok(RequestDto.from(request, taskDtos));
    }

    private void validateRundownEditor(UserContext user) {
        if (user == null) {
            throw new ForbiddenAppException("update_rundown");
        }
        if (!user.hasRole("DEVELOPER") && !user.hasRole("TL") && !user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException("update_rundown");
        }
    }

    private void validateArchivedViewer(boolean includeArchived, UserContext user) {
        if (!includeArchived) {
            return;
        }
        validateAdmin(user, "view_archived_rundown");
    }

    private void validateViewMode(String view) {
        if ("flow".equalsIgnoreCase(view) || "stitched".equalsIgnoreCase(view)) {
            return;
        }
        throw new ValidationAppException("Invalid view parameter: '" + view + "'. Must be flow or stitched.");
    }

    private void validateAttemptView(String attemptView) {
        if ("latest".equalsIgnoreCase(attemptView) || "history".equalsIgnoreCase(attemptView)) {
            return;
        }
        throw new ValidationAppException(
                "Invalid attemptView parameter: '" + attemptView + "'. Must be latest or history.");
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

    private List<Request> filterVisibleRequests(List<Request> requests, UserContext user) {
        if (user == null || user.isGlobalDevOpsAdmin()) {
            return requests;
        }
        return requests.stream()
                .filter(request -> user.hasScopedAccess(request.getApplication(), request.getSnowGroup()))
                .toList();
    }

    private Request findRequestForScopeValidation(String flowId, String requestId, boolean includeArchived) {
        return releaseFlowService.findRequestsForFlow(flowId, includeArchived).stream()
                .filter(request -> request.getId().equals(requestId))
                .findFirst()
                .orElseThrow(() -> new ForbiddenAppException("request_scope_lookup"));
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

    private List<String> parseLinkedFlowIds(String linked) {
        if (linked == null || linked.isBlank()) {
            return List.of();
        }

        return java.util.Arrays.stream(linked.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .distinct()
                .toList();
    }
}

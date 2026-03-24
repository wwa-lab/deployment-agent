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
            @RequestParam(defaultValue = "false") boolean includeArchived,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @AuthenticationPrincipal UserContext user) {

        if (page < 0) throw new ValidationAppException("Invalid page parameter", page);
        if (size < 1) throw new ValidationAppException("Invalid size parameter", size);
        if (size > 100) throw new ValidationAppException("Page size cannot exceed 100", size);
        validateArchivedViewer(includeArchived, user);

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<ReleaseFlow> result = releaseFlowService.list(project, status, stage, pageable, includeArchived);
        Map<String, List<Request>> requestsByReleaseFlowId = releaseFlowService.findRequestsByReleaseFlowIds(
                result.getContent().stream().map(ReleaseFlow::getId).toList(),
                includeArchived);

        List<ReleaseFlowListItemDto> dtos = result.getContent().stream()
                .map(releaseFlow -> ReleaseFlowListItemDto.from(
                        releaseFlow,
                        requestsByReleaseFlowId.getOrDefault(releaseFlow.getId(), List.of())))
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

        ReleaseFlow rf = releaseFlowService.getById(id, includeArchived);
        List<RequestDto> requestDtos = releaseFlowService.findRequestsForFlow(id, includeArchived).stream()
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
                requestDtos));
    }

    @PatchMapping("/{flowId}/requests/{requestId}/rundown")
    public ResponseEntity<RequestDto> updateRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @RequestBody RequestRundownUpdateDto body,
            @AuthenticationPrincipal UserContext user) {
        validateRundownEditor(user);

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
        return ResponseEntity.ok(releaseFlowService.archiveRequestRundown(flowId, requestId, user));
    }

    @PostMapping("/{flowId}/requests/{requestId}/restore")
    public ResponseEntity<RequestArchiveResultDto> restoreRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "restore_rundown");
        return ResponseEntity.ok(releaseFlowService.restoreRequestRundown(flowId, requestId, user));
    }

    @DeleteMapping("/{flowId}/requests/{requestId}/purge")
    public ResponseEntity<RequestPurgeResultDto> purgeArchivedRequestRundown(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "purge_rundown");
        return ResponseEntity.ok(releaseFlowService.purgeArchivedRequestRundown(flowId, requestId, user));
    }

    @PostMapping("/{flowId}/requests/{requestId}/start")
    public ResponseEntity<RequestDto> startRequestDeployment(
            @PathVariable String flowId,
            @PathVariable String requestId,
            @AuthenticationPrincipal UserContext user) {
        validateRundownEditor(user);

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
        validateRundownEditor(user);

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

    private void validateAdmin(UserContext user, String action) {
        if (user == null || !user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException(action);
        }
    }
}

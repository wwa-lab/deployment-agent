package com.wwa.deploymentagent.agents.build.web;

import com.wwa.deploymentagent.contracts.AgentId;
import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.*;
import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowFilter;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowService;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import com.wwa.deploymentagent.errors.ValidationAppException;
import com.wwa.deploymentagent.platform.web.security.AgentBoundaryGuard;
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
 *   GET /api/build-agent/release-flows           – DEV-stage list scoped to build-agent
 *   GET /api/build-agent/release-flows/:id       – single flow detail (no stitching)
 * </pre>
 *
 * <p>PL-6 boundary: every endpoint forces {@code agent = "build-agent"} server-side.
 * No stitching — `?linked=` is ignored on getById (BA-3).
 */
@RestController
@RequestMapping("/api/build-agent/release-flows")
@RequiredArgsConstructor
public class BuildReleaseFlowController {

    private final ReleaseFlowService releaseFlowService;
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
                .map(req -> RequestDto.from(req, req.getTasks().stream().map(TaskDto::from).toList()))
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
}

package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.dto.*;
import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlowService;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
import com.wwa.deploymentagent.domain.task.TaskService;
import com.wwa.deploymentagent.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

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
    private final RequestRepository requestRepository;
    private final TaskService taskService;

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<ReleaseFlowListItemDto>> list(
            @RequestParam(required = false) String project,
            @RequestParam(required = false) FlowStatus status,
            @RequestParam(required = false) Stage stage,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {

        if (page < 0) throw new ValidationAppException("Invalid page parameter", page);
        if (size < 1) throw new ValidationAppException("Invalid size parameter", size);
        if (size > 100) throw new ValidationAppException("Page size cannot exceed 100", size);

        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"));
        Page<ReleaseFlow> result = releaseFlowService.list(project, status, stage, pageable);

        List<ReleaseFlowListItemDto> dtos = result.getContent().stream()
                .map(ReleaseFlowListItemDto::from)
                .toList();

        return ResponseEntity.ok(new PaginatedResponseDto<>(
                dtos, result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ReleaseFlowDetailDto> getById(@PathVariable String id) {
        ReleaseFlow rf = releaseFlowService.getById(id);
        List<Request> requests = requestRepository.findByReleaseFlowIdWithTasks(id);

        List<RequestDto> requestDtos = requests.stream()
                .map(req -> {
                    List<TaskDto> taskDtos = req.getTasks().stream()
                            .map(TaskDto::from)
                            .toList();
                    return RequestDto.from(req, taskDtos);
                })
                .toList();

        ReleaseFlowDetailDto detail = new ReleaseFlowDetailDto(
                rf.getId(), rf.getProjectId(), rf.getProjectName(),
                rf.getReleaseId(), rf.getNormalizedReleaseId(),
                rf.getCurrentStage(), rf.getFlowStatus(), rf.getReviewStatus(),
                requestDtos);

        return ResponseEntity.ok(detail);
    }
}

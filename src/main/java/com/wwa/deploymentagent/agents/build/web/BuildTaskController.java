package com.wwa.deploymentagent.agents.build.web;

import com.wwa.deploymentagent.contracts.AgentId;
import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.RecordResultRequestDto;
import com.wwa.deploymentagent.contracts.dto.TaskDto;
import com.wwa.deploymentagent.contracts.dto.TaskExecutionHistoryDto;
import com.wwa.deploymentagent.contracts.enums.ExecutionType;
import com.wwa.deploymentagent.domain.execution.AutoExecutionService;
import com.wwa.deploymentagent.domain.task.RecordResultService;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistoryService;
import com.wwa.deploymentagent.domain.task.TaskService;
import com.wwa.deploymentagent.errors.ValidationAppException;
import com.wwa.deploymentagent.platform.web.security.AgentBoundaryGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Build Agent task controller (BA-T21).
 *
 * <p>Every ID-bearing endpoint calls {@link AgentBoundaryGuard} with
 * {@link AgentId#BUILD_AGENT}, so cross-agent probes (e.g. from Testing or
 * Deployment) receive 404. No stage progression at the task level.
 */
@RestController
@RequestMapping("/api/build-agent/tasks")
@RequiredArgsConstructor
public class BuildTaskController {

    private final TaskService taskService;
    private final TaskExecutionHistoryService executionHistoryService;
    private final RecordResultService recordResultService;
    private final AutoExecutionService autoExecutionService;
    private final AgentBoundaryGuard boundaryGuard;

    @GetMapping
    public ResponseEntity<List<TaskDto>> listByRequest(@RequestParam String requestId) {
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.BUILD_AGENT);
        List<TaskDto> dtos = taskService.listByRequestId(requestId)
                .stream()
                .map(TaskDto::from)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDto> getById(@PathVariable String id) {
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT);
        return ResponseEntity.ok(TaskDto.from(taskService.getById(id)));
    }

    @PutMapping("/{id}/input")
    public ResponseEntity<TaskDto> editInput(
            @PathVariable String id,
            @RequestBody Map<String, Object> newInput,
            @AuthenticationPrincipal UserContext user) {
        if (newInput == null) {
            throw new ValidationAppException("Request body must not be null");
        }
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT);
        return ResponseEntity.ok(TaskDto.from(taskService.editInput(id, newInput, user)));
    }

    @PutMapping("/{id}/execution-type")
    public ResponseEntity<TaskDto> editExecutionType(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserContext user) {
        String value = body != null ? body.get("executionType") : null;
        if (value == null) {
            throw new ValidationAppException("executionType is required");
        }
        ExecutionType newType;
        try {
            newType = ExecutionType.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw new ValidationAppException("Invalid executionType: " + value + ". Must be MANUAL or AUTO");
        }
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT);
        return ResponseEntity.ok(TaskDto.from(taskService.editExecutionType(id, newType, user)));
    }

    @PutMapping("/{id}/names")
    public ResponseEntity<TaskDto> editNames(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT);
        return ResponseEntity.ok(TaskDto.from(
                taskService.editNames(id, body.get("taskName"), body.get("taskGroupName"), user)));
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<List<TaskExecutionHistoryDto>> getExecutions(@PathVariable String id) {
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT);
        List<TaskExecutionHistoryDto> dtos = executionHistoryService.findByTaskId(id)
                .stream()
                .map(TaskExecutionHistoryDto::from)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/{id}/clone")
    public ResponseEntity<TaskDto> cloneTask(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT);
        return ResponseEntity.ok(TaskDto.from(taskService.cloneTask(id, user)));
    }

    @PutMapping("/reorder")
    public ResponseEntity<List<TaskDto>> reorderTasks(
            @RequestBody Map<String, Object> body,
            @AuthenticationPrincipal UserContext user) {
        @SuppressWarnings("unchecked")
        List<String> orderedIds = (List<String>) body.get("taskIds");
        String requestId = (String) body.get("requestId");
        if (orderedIds == null || requestId == null) {
            throw new ValidationAppException("requestId and taskIds are required");
        }
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.BUILD_AGENT);
        return ResponseEntity.ok(
                taskService.reorderTasks(requestId, orderedIds, user).stream()
                        .map(TaskDto::from)
                        .toList());
    }

    @PostMapping("/{id}/record-result")
    public ResponseEntity<TaskDto> recordResult(
            @PathVariable String id,
            @RequestBody RecordResultRequestDto body,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT);
        return ResponseEntity.ok(
                TaskDto.from(recordResultService.recordResult(
                        id, body.resultSummary(), body.resultLogs(), user)));
    }

    @PostMapping("/{id}/start-manual")
    public ResponseEntity<TaskDto> startManualExecution(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT);
        return ResponseEntity.ok(TaskDto.from(taskService.startManualExecution(id, user)));
    }

    @PostMapping("/{id}/submit-auto")
    public ResponseEntity<TaskDto> submitAutoExecution(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.BUILD_AGENT);
        return ResponseEntity.ok(
                TaskDto.from(autoExecutionService.submitAutoExecution(id, user)));
    }
}

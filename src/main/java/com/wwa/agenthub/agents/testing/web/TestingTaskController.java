package com.wwa.agenthub.agents.testing.web;

import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.RecordResultRequestDto;
import com.wwa.agenthub.contracts.dto.TaskDto;
import com.wwa.agenthub.contracts.dto.TaskExecutionHistoryDto;
import com.wwa.agenthub.contracts.enums.ExecutionType;
import com.wwa.agenthub.domain.execution.AutoExecutionService;
import com.wwa.agenthub.domain.task.RecordResultService;
import com.wwa.agenthub.domain.task.TaskExecutionHistoryService;
import com.wwa.agenthub.domain.task.TaskService;
import com.wwa.agenthub.errors.ValidationAppException;
import com.wwa.agenthub.platform.web.security.AgentBoundaryGuard;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Testing Agent task controller (BA-T20).
 *
 * <pre>
 *   GET  /api/testing-agent/tasks?requestId=X   – list tasks for a request
 *   GET  /api/testing-agent/tasks/:id            – single task detail
 *   PUT  /api/testing-agent/tasks/:id/input      – edit task input (owner or DEVOPS_ADMIN)
 *   GET  /api/testing-agent/tasks/:id/executions – execution history
 *   POST /api/testing-agent/tasks/:id/record-result  – record manual task result
 *   POST /api/testing-agent/tasks/:id/start-manual   – start manual task execution
 *   POST /api/testing-agent/tasks/:id/submit-auto    – submit AUTO task for external execution
 * </pre>
 *
 * <p>Every ID-bearing endpoint calls {@link AgentBoundaryGuard} — closing v2 R-08
 * (cross-agent task probe leak).
 */
@RestController
@RequestMapping("/api/testing-agent/tasks")
@RequiredArgsConstructor
public class TestingTaskController {

    private final TaskService taskService;
    private final TaskExecutionHistoryService executionHistoryService;
    private final RecordResultService recordResultService;
    private final AutoExecutionService autoExecutionService;
    private final AgentBoundaryGuard boundaryGuard;

    @GetMapping
    public ResponseEntity<List<TaskDto>> listByRequest(
            @RequestParam String requestId,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertLegacyRequestReadable(requestId, AgentId.TESTING_AGENT, user);
        List<TaskDto> dtos = taskService.listByRequestId(requestId)
                .stream()
                .filter(task -> !task.isIntegrationBound())
                .map(TaskDto::from)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDto> getById(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertLegacyTaskReadable(id, AgentId.TESTING_AGENT, user);
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
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.TESTING_AGENT);
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
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.TESTING_AGENT);
        return ResponseEntity.ok(TaskDto.from(taskService.editExecutionType(id, newType, user)));
    }

    @PutMapping("/{id}/names")
    public ResponseEntity<TaskDto> editNames(
            @PathVariable String id,
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.TESTING_AGENT);
        return ResponseEntity.ok(TaskDto.from(
                taskService.editNames(id, body.get("taskName"), body.get("taskGroupName"), user)));
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<List<TaskExecutionHistoryDto>> getExecutions(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertLegacyTaskReadable(id, AgentId.TESTING_AGENT, user);
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
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.TESTING_AGENT);
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
        boundaryGuard.assertRequestBelongsToAgent(requestId, AgentId.TESTING_AGENT);
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
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.TESTING_AGENT);
        return ResponseEntity.ok(
                TaskDto.from(recordResultService.recordResult(
                        id, body.resultSummary(), body.resultLogs(), user)));
    }

    @PostMapping("/{id}/start-manual")
    public ResponseEntity<TaskDto> startManualExecution(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.TESTING_AGENT);
        return ResponseEntity.ok(TaskDto.from(taskService.startManualExecution(id, user)));
    }

    @PostMapping("/{id}/submit-auto")
    public ResponseEntity<TaskDto> submitAutoExecution(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.TESTING_AGENT);
        return ResponseEntity.ok(
                TaskDto.from(autoExecutionService.submitAutoExecution(id, user)));
    }
}

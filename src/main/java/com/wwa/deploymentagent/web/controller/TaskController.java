package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.RecordResultRequestDto;
import com.wwa.deploymentagent.contracts.dto.TaskDto;
import com.wwa.deploymentagent.contracts.dto.TaskExecutionHistoryDto;
import com.wwa.deploymentagent.domain.execution.AutoExecutionService;
import com.wwa.deploymentagent.domain.task.RecordResultService;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistoryService;
import com.wwa.deploymentagent.domain.task.TaskService;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import com.wwa.deploymentagent.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Task controller – REST endpoints for Task lifecycle.
 *
 * <pre>
 *   GET  /api/deployment-agent/tasks?requestId=X   – list tasks for a request
 *   GET  /api/deployment-agent/tasks/:id            – single task detail
 *   PUT  /api/deployment-agent/tasks/:id/input      – edit task input (TL only)
 *   GET  /api/deployment-agent/tasks/:id/executions – execution history
 * </pre>
 */
@RestController
@RequestMapping("/api/deployment-agent/tasks")
@RequiredArgsConstructor
public class TaskController {

    private final TaskService taskService;
    private final TaskExecutionHistoryService executionHistoryService;
    private final RecordResultService recordResultService;
    private final AutoExecutionService autoExecutionService;

    @GetMapping
    public ResponseEntity<List<TaskDto>> listByRequest(@RequestParam String requestId) {
        List<TaskDto> dtos = taskService.listByRequestId(requestId)
                .stream()
                .map(TaskDto::from)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TaskDto> getById(@PathVariable String id) {
        return ResponseEntity.ok(TaskDto.from(taskService.getById(id)));
    }

    @PutMapping("/{id}/input")
    public ResponseEntity<TaskDto> editInput(
            @PathVariable String id,
            @RequestBody Map<String, Object> newInput,
            @AuthenticationPrincipal UserContext user) {
        if (!"TL".equals(user.role())) {
            throw new ForbiddenAppException("task:editInput");
        }
        if (newInput == null) {
            throw new ValidationAppException("Request body must not be null");
        }

        return ResponseEntity.ok(TaskDto.from(taskService.editInput(id, newInput, user)));
    }

    @GetMapping("/{id}/executions")
    public ResponseEntity<List<TaskExecutionHistoryDto>> getExecutions(@PathVariable String id) {
        List<TaskExecutionHistoryDto> dtos = executionHistoryService.findByTaskId(id)
                .stream()
                .map(TaskExecutionHistoryDto::from)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    /**
     * Record the result of a MANUAL task (TL only).
     * Guards: task must be MANUAL + in Ready_For_Execution state.
     */
    @PostMapping("/{id}/record-result")
    public ResponseEntity<TaskDto> recordResult(
            @PathVariable String id,
            @RequestBody RecordResultRequestDto body,
            @AuthenticationPrincipal UserContext user) {
        if (!"TL".equals(user.role())) {
            throw new ForbiddenAppException("task:recordResult");
        }

        return ResponseEntity.ok(
                TaskDto.from(recordResultService.recordResult(
                        id, body.resultSummary(), body.resultLogs(), user)));
    }

    /**
     * Submit an AUTO task for external execution (TL or DEVOPS_ADMIN only).
     * Guards: task must be AUTO + in Ready_For_Execution state.
     */
    @PostMapping("/{id}/submit-auto")
    public ResponseEntity<TaskDto> submitAutoExecution(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        if (!"TL".equals(user.role()) && !"DEVOPS_ADMIN".equals(user.role())) {
            throw new ForbiddenAppException("task:submitAutoExecution");
        }

        return ResponseEntity.ok(
                TaskDto.from(autoExecutionService.submitAutoExecution(id, user)));
    }
}

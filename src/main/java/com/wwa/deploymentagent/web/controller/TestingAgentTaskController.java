package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.DecisionRequestDto;
import com.wwa.deploymentagent.contracts.dto.RecordResultRequestDto;
import com.wwa.deploymentagent.contracts.dto.TaskDto;
import com.wwa.deploymentagent.contracts.dto.TaskExecutionHistoryDto;
import com.wwa.deploymentagent.domain.decision.DecisionEngine;
import com.wwa.deploymentagent.domain.decision.ReleaseFlowProgressionService;
import com.wwa.deploymentagent.domain.execution.AutoExecutionService;
import com.wwa.deploymentagent.domain.task.RecordResultService;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistoryService;
import com.wwa.deploymentagent.domain.task.TaskService;
import com.wwa.deploymentagent.errors.ValidationAppException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Testing Agent Task controller – combines Task and Decision endpoints.
 *
 * <pre>
 *   GET  /api/testing-agent/tasks?requestId=X   – list tasks for a request
 *   GET  /api/testing-agent/tasks/:id            – single task detail
 *   PUT  /api/testing-agent/tasks/:id/input      – edit task input (owner or DEVOPS_ADMIN)
 *   GET  /api/testing-agent/tasks/:id/executions – execution history
 *   POST /api/testing-agent/tasks/:id/record-result  – record manual task result
 *   POST /api/testing-agent/tasks/:id/start-manual   – start manual task execution
 *   POST /api/testing-agent/tasks/:id/submit-auto    – submit AUTO task for external execution
 *   POST /api/testing-agent/tasks/:id/decision       – apply task decision
 * </pre>
 */
@RestController
@RequestMapping("/api/testing-agent/tasks")
@RequiredArgsConstructor
public class TestingAgentTaskController {

    private final TaskService taskService;
    private final TaskExecutionHistoryService executionHistoryService;
    private final RecordResultService recordResultService;
    private final AutoExecutionService autoExecutionService;
    private final DecisionEngine decisionEngine;
    private final ReleaseFlowProgressionService progressionService;

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
     * Record the result of a MANUAL task (owner or DEVOPS_ADMIN only).
     * Guards: task must be MANUAL + in Ready_For_Execution or Executing state.
     */
    @PostMapping("/{id}/record-result")
    public ResponseEntity<TaskDto> recordResult(
            @PathVariable String id,
            @RequestBody RecordResultRequestDto body,
            @AuthenticationPrincipal UserContext user) {
        return ResponseEntity.ok(
                TaskDto.from(recordResultService.recordResult(
                        id, body.resultSummary(), body.resultLogs(), user)));
    }

    /**
     * Start a MANUAL task execution (owner or DEVOPS_ADMIN only).
     * Guards: task must be MANUAL + in Ready_For_Execution state.
     */
    @PostMapping("/{id}/start-manual")
    public ResponseEntity<TaskDto> startManualExecution(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        return ResponseEntity.ok(TaskDto.from(taskService.startManualExecution(id, user)));
    }

    /**
     * Submit an AUTO task for external execution (owner or DEVOPS_ADMIN only).
     * Guards: task must be AUTO + in Ready_For_Execution state.
     */
    @PostMapping("/{id}/submit-auto")
    public ResponseEntity<TaskDto> submitAutoExecution(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        return ResponseEntity.ok(
                TaskDto.from(autoExecutionService.submitAutoExecution(id, user)));
    }

    /**
     * Apply a decision to a task (approve/reject/rerun/skip).
     * Auth: task owner or DEVOPS_ADMIN required (enforced in DecisionEngine).
     */
    @PostMapping("/{id}/decision")
    public ResponseEntity<TaskDto> applyDecision(
            @PathVariable String id,
            @Valid @RequestBody DecisionRequestDto body,
            @AuthenticationPrincipal UserContext user) {

        decisionEngine.applyDecision(id, body.decision(), user, body.comment());
        progressionService.progressAfterDecision(id);

        return ResponseEntity.ok(TaskDto.from(taskService.getById(id)));
    }
}

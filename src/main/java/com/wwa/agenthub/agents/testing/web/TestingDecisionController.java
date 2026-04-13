package com.wwa.agenthub.agents.testing.web;

import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.DecisionRequestDto;
import com.wwa.agenthub.contracts.dto.TaskDto;
import com.wwa.agenthub.domain.decision.DecisionEngine;
import com.wwa.agenthub.domain.decision.ReleaseFlowProgressionService;
import com.wwa.agenthub.domain.task.TaskService;
import com.wwa.agenthub.platform.web.security.AgentBoundaryGuard;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Testing Agent decision controller (BA-T20 — new in v3).
 *
 * <pre>
 *   POST /api/testing-agent/tasks/:id/decision
 * </pre>
 *
 * <p>The task ID is guarded via {@link AgentBoundaryGuard} before the decision engine
 * runs so cross-agent probes receive 404 (PL-9, closes v2 R-08).
 */
@RestController
@RequestMapping("/api/testing-agent/tasks")
@RequiredArgsConstructor
public class TestingDecisionController {

    private final DecisionEngine decisionEngine;
    private final ReleaseFlowProgressionService progressionService;
    private final TaskService taskService;
    private final AgentBoundaryGuard boundaryGuard;

    @PostMapping("/{id}/decision")
    public ResponseEntity<TaskDto> applyDecision(
            @PathVariable String id,
            @Valid @RequestBody DecisionRequestDto body,
            @AuthenticationPrincipal UserContext user) {

        boundaryGuard.assertTaskBelongsToAgent(id, AgentId.TESTING_AGENT);
        decisionEngine.applyDecision(id, body.decision(), user, body.comment());
        progressionService.progressAfterDecision(id);

        return ResponseEntity.ok(TaskDto.from(taskService.getById(id)));
    }
}

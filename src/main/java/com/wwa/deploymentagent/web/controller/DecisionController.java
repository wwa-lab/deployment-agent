package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.DecisionRequestDto;
import com.wwa.deploymentagent.contracts.dto.TaskDto;
import com.wwa.deploymentagent.domain.decision.DecisionEngine;
import com.wwa.deploymentagent.domain.decision.ReleaseFlowProgressionService;
import com.wwa.deploymentagent.domain.task.TaskService;
import com.wwa.deploymentagent.web.security.UserContextAuthentication;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/**
 * Decision controller – REST endpoint for applying task decisions.
 *
 * <pre>
 *   POST /api/deployment-agent/tasks/:id/decision
 *   Request:  { decision: "approve"|"reject"|"rerun"|"skip", comment?: string }
 *   Auth:     TL role required (enforced in DecisionEngine)
 *   Response: Updated TaskDto
 * </pre>
 */
@RestController
@RequestMapping("/api/deployment-agent/tasks")
@RequiredArgsConstructor
public class DecisionController {

    private final DecisionEngine decisionEngine;
    private final ReleaseFlowProgressionService progressionService;
    private final TaskService taskService;

    @PostMapping("/{id}/decision")
    public ResponseEntity<TaskDto> applyDecision(
            @PathVariable String id,
            @Valid @RequestBody DecisionRequestDto body,
            @AuthenticationPrincipal UserContextAuthentication auth) {

        UserContext user = auth.getPrincipal();

        decisionEngine.applyDecision(id, body.decision(), user, body.comment());
        progressionService.progressAfterDecision(id);

        return ResponseEntity.ok(TaskDto.from(taskService.getById(id)));
    }
}

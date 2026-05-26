package com.wwa.agenthub.platform.web.shared;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.AgentContributionDashboardStatusDto;
import com.wwa.agenthub.domain.configuration.AgentContributionDashboardConfigService;
import com.wwa.agenthub.errors.ForbiddenAppException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/agent-contribute-dashboard")
@RequiredArgsConstructor
public class AgentContributionDashboardController {

    private final AgentContributionDashboardConfigService configService;

    @GetMapping("/statuses")
    public ResponseEntity<AgentContributionDashboardStatusDto> getStatuses() {
        return ResponseEntity.ok(configService.getStatuses());
    }

    @PutMapping("/statuses")
    public ResponseEntity<AgentContributionDashboardStatusDto> updateStatuses(
            @Valid @RequestBody AgentContributionDashboardStatusDto.UpsertRequest body,
            @AuthenticationPrincipal UserContext user) {
        if (!user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException("agent-contribute-dashboard:update-status");
        }
        return ResponseEntity.ok(configService.updateStatuses(body, user));
    }
}

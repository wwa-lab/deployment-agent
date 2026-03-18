package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.AuditLogEntryDto;
import com.wwa.deploymentagent.contracts.dto.PaginatedResponseDto;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.domain.audit.AuditLogEntry;
import com.wwa.deploymentagent.domain.audit.AuditLogRepository;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import com.wwa.deploymentagent.web.security.UserContextAuthentication;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Set;

/**
 * Audit Log controller.
 *
 * <pre>
 *   GET /api/deployment-agent/audit-logs  – paginated list (AUDIT | MANAGEMENT | DEVOPS_ADMIN only)
 * </pre>
 */
@RestController
@RequestMapping("/api/deployment-agent/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private static final Set<String> ALLOWED_ROLES = Set.of("AUDIT", "MANAGEMENT", "DEVOPS_ADMIN");

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<AuditLogEntryDto>> list(
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) AuditActionType actionType,
            @RequestParam(required = false) String releaseFlowId,
            @RequestParam(required = false) String taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserContextAuthentication auth) {

        UserContext user = auth.getPrincipal();
        if (!ALLOWED_ROLES.contains(user.role())) {
            throw new ForbiddenAppException("audit-logs:read");
        }

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "timestamp"));

        Page<AuditLogEntry> result;
        if (operatorId != null) {
            result = auditLogRepository.findByOperatorId(operatorId, pageable);
        } else if (actionType != null) {
            result = auditLogRepository.findByActionType(actionType, pageable);
        } else if (releaseFlowId != null) {
            result = auditLogRepository.findByReleaseFlowId(releaseFlowId, pageable);
        } else if (taskId != null) {
            result = auditLogRepository.findByTaskId(taskId, pageable);
        } else {
            result = auditLogRepository.findAll(pageable);
        }

        List<AuditLogEntryDto> dtos = result.getContent().stream()
                .map(AuditLogEntryDto::from)
                .toList();

        return ResponseEntity.ok(new PaginatedResponseDto<>(
                dtos, result.getTotalElements(), result.getNumber(), result.getSize()));
    }
}

package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.dto.AuditLogEntryDto;
import com.wwa.deploymentagent.contracts.dto.PaginatedResponseDto;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.domain.audit.AuditLogEntry;
import com.wwa.deploymentagent.domain.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * Audit Log controller.
 *
 * <pre>
 *   GET /api/deployment-agent/audit-logs  – paginated list for any authenticated user
 * </pre>
 */
@RestController
@RequestMapping("/api/deployment-agent/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<AuditLogEntryDto>> list(
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) AuditActionType actionType,
            @RequestParam(required = false) String releaseFlowId,
            @RequestParam(required = false) String taskId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

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

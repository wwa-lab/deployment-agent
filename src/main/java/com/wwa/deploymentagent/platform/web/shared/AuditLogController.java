package com.wwa.deploymentagent.platform.web.shared;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.AuditLogEntryDto;
import com.wwa.deploymentagent.contracts.dto.PaginatedResponseDto;
import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.domain.audit.AuditLogEntry;
import com.wwa.deploymentagent.domain.audit.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
/**
 * Audit Log controller.
 *
 * <pre>
 *   GET /api/platform/audit-logs  – paginated list for any authenticated user
 * </pre>
 */
@RestController
@RequestMapping("/api/platform/audit-logs")
@RequiredArgsConstructor
public class AuditLogController {

    private final AuditLogRepository auditLogRepository;

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<AuditLogEntryDto>> list(
            @RequestParam(required = false) String operatorId,
            @RequestParam(required = false) AuditActionType actionType,
            @RequestParam(required = false) String releaseFlowId,
            @RequestParam(required = false) String taskId,
            @RequestParam(required = false) String application,
            @RequestParam(required = false) String snowGroup,
            @RequestParam(required = false) String agent,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserContext user) {

        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Direction.DESC, "timestamp"));

        Specification<AuditLogEntry> specification = Specification.where(null);
        specification = specification.and(equalIfPresent("operatorId", normalizeBlank(operatorId)));
        specification = specification.and(equalIfPresent("actionType", actionType));
        specification = specification.and(equalIfPresent("releaseFlowId", normalizeBlank(releaseFlowId)));
        specification = specification.and(equalIfPresent("taskId", normalizeBlank(taskId)));
        specification = specification.and(equalIfPresent("application", normalizeBlank(application)));
        specification = specification.and(equalIfPresent("snowGroup", normalizeBlank(snowGroup)));
        specification = specification.and(equalIfPresent("agent", normalizeBlank(agent)));

        Page<AuditLogEntry> result;
        if (user == null || user.isGlobalDevOpsAdmin()) {
            result = auditLogRepository.findAll(specification, pageable);
        } else {
            List<AuditLogEntry> filtered = auditLogRepository.findAll(specification, Sort.by(Sort.Direction.DESC, "timestamp"))
                    .stream()
                    .filter(entry -> user.hasScopedAccess(entry.getApplication(), entry.getSnowGroup()))
                    .toList();
            int fromIndex = Math.min((int) pageable.getOffset(), filtered.size());
            int toIndex = Math.min(fromIndex + pageable.getPageSize(), filtered.size());
            result = new PageImpl<>(filtered.subList(fromIndex, toIndex), pageable, filtered.size());
        }

        List<AuditLogEntryDto> dtos = result.getContent().stream()
                .map(AuditLogEntryDto::from)
                .toList();

        return ResponseEntity.ok(new PaginatedResponseDto<>(
                dtos, result.getTotalElements(), result.getNumber(), result.getSize()));
    }

    private <T> Specification<AuditLogEntry> equalIfPresent(String fieldName, T value) {
        if (value == null) {
            return null;
        }
        return (root, query, criteriaBuilder) -> criteriaBuilder.equal(root.get(fieldName), value);
    }

    private String normalizeBlank(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}

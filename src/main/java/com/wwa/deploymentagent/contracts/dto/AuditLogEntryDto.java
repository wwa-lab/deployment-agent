package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import com.wwa.deploymentagent.domain.audit.AuditLogEntry;

import java.time.Instant;
import java.util.Map;

public record AuditLogEntryDto(
        String id,
        Instant timestamp,
        String operatorId,
        String operatorRole,
        AuditActionType actionType,
        String releaseFlowId,
        String requestId,
        String taskId,
        Map<String, Object> contextPayload
) {
    public static AuditLogEntryDto from(AuditLogEntry entry) {
        return new AuditLogEntryDto(
                entry.getId(),
                entry.getTimestamp(),
                entry.getOperatorId(),
                entry.getOperatorRole(),
                entry.getActionType(),
                entry.getReleaseFlowId(),
                entry.getRequestId(),
                entry.getTaskId(),
                entry.getContextPayload()
        );
    }
}

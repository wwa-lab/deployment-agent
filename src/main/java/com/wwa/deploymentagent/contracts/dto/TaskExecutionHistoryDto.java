package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.ExecutionStatus;
import com.wwa.deploymentagent.contracts.enums.ExternalStatus;
import com.wwa.deploymentagent.domain.task.TaskExecutionHistory;

import java.time.Instant;
import java.util.Map;

public record TaskExecutionHistoryDto(
        String id,
        String taskId,
        int attemptNumber,
        ExecutionStatus executionStatus,
        Map<String, Object> inputSnapshot,
        Map<String, Object> resultSummary,
        String resultLogs,
        Instant startTime,
        Instant endTime,
        // Submit-time external metadata
        String externalSystemType,
        String externalExecutionId,
        String externalJobUrl,
        Instant submittedAt,
        String submissionStatus,
        String submissionMessage,
        String configApplication,
        String configSnowGroup,
        String configAgent,
        // Polling-time external metadata
        ExternalStatus externalStatus,
        String externalStatusMessage,
        String externalLogUrl,
        String externalApprovalUrl,
        Instant lastSyncedAt
) {
    public static TaskExecutionHistoryDto from(TaskExecutionHistory h) {
        return new TaskExecutionHistoryDto(
                h.getId(),
                h.getTask().getId(),
                h.getAttemptNumber(),
                h.getExecutionStatus(),
                h.getInputSnapshot(),
                h.getResultSummary(),
                h.getResultLogs(),
                h.getStartTime(),
                h.getEndTime(),
                h.getExternalSystemType(),
                h.getExternalExecutionId(),
                h.getExternalJobUrl(),
                h.getSubmittedAt(),
                h.getSubmissionStatus(),
                h.getSubmissionMessage(),
                h.getConfigApplication(),
                h.getConfigSnowGroup(),
                h.getConfigAgent(),
                h.getExternalStatus(),
                h.getExternalStatusMessage(),
                h.getExternalLogUrl(),
                h.getExternalApprovalUrl(),
                h.getLastSyncedAt()
        );
    }
}

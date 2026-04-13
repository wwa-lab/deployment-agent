package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.ExecutionType;
import com.wwa.deploymentagent.contracts.enums.TaskStatus;
import com.wwa.deploymentagent.domain.task.Task;

import java.time.Instant;
import java.util.Map;

public record TaskDto(
        String id,
        String requestId,
        String category,
        String dependencies,
        String taskGroupId,
        String taskGroupName,
        int stepSeq,
        String taskName,
        ExecutionType executionType,
        boolean critical,
        TaskStatus taskStatus,
        Map<String, Object> inputParameters,
        String expectedOutput,
        String owner,
        Instant plannedStartTime,
        Instant plannedEndTime,
        Map<String, Object> customFields,
        Map<String, Object> currentResultSummary,
        String latestExecutionId,
        long version
) {
    public static TaskDto from(Task task) {
        return new TaskDto(
                task.getId(),
                task.getRequest().getId(),
                task.getImportMetadata() != null
                        ? (String) task.getImportMetadata().get("activity_category")
                        : null,
                task.getImportMetadata() != null
                        ? (String) task.getImportMetadata().get("dependencies")
                        : null,
                task.getTaskGroupId(),
                task.getTaskGroupName(),
                task.getStepSeq(),
                task.getTaskName(),
                task.getExecutionType(),
                task.isCritical(),
                task.getTaskStatus(),
                task.getInputParameters(),
                task.getExpectedOutput(),
                task.getOwner(),
                task.getPlannedStartTime(),
                task.getPlannedEndTime(),
                task.getCustomFields(),
                task.getCurrentResultSummary(),
                task.getLatestExecutionId(),
                task.getVersion() != null ? task.getVersion() : 0L
        );
    }
}

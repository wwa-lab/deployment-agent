package com.wwa.deploymentagent.domain.task;

import com.wwa.deploymentagent.contracts.enums.ExecutionType;
import com.wwa.deploymentagent.domain.releaseflow.Request;

import java.time.Instant;
import java.util.Map;

/**
 * Input record for creating a new Task (used by Import Service and tests).
 */
public record CreateTaskInput(
        Request request,
        String taskGroupId,
        String taskGroupName,
        int stepSeq,
        String taskName,
        ExecutionType executionType,
        boolean critical,
        Map<String, Object> inputParameters,
        String expectedOutput,
        String owner,
        Instant plannedStartTime,
        Instant plannedEndTime,
        Map<String, Object> importMetadata
) {}

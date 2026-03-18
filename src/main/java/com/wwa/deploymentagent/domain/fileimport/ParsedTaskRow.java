package com.wwa.deploymentagent.domain.fileimport;

import com.wwa.deploymentagent.contracts.enums.ExecutionType;

import java.time.Instant;
import java.util.Map;

public record ParsedTaskRow(
        String projectId,
        String projectName,
        String taskGroupId,
        String taskGroupName,
        int stepSeq,
        String taskName,
        ExecutionType executionType,
        Map<String, Object> inputParameters,
        String expectedOutput,
        String owner,
        Instant plannedStartTime,
        Instant plannedEndTime,
        Map<String, Object> importMetadata
) {}

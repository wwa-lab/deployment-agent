package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.contracts.enums.ExecutionType;

public record CreateRundownFromTemplateTaskDto(
        String category,
        String taskName,
        Integer step,
        String stepName,
        ExecutionType type,
        Boolean critical,
        String owner,
        Integer estDurationMinutes,
        String dependencies
) {}

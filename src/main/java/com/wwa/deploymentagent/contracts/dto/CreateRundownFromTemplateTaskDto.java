package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.ExecutionType;

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

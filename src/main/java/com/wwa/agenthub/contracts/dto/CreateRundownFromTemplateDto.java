package com.wwa.agenthub.contracts.dto;

import java.util.List;

public record CreateRundownFromTemplateDto(
        String templateId,
        String templateName,
        String projectId,
        String projectName,
        String stage,
        String releaseId,
        String snowGroup,
        String application,
        String agent,
        String site,
        String owner,
        Integer estimatedRemainingMinutes,
        List<CreateRundownFromTemplateTaskDto> tasks
) {}

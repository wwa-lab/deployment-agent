package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.Stage;

import java.util.List;

public record CreateRundownFromTemplateDto(
        String templateId,
        String templateName,
        String projectId,
        String projectName,
        Stage stage,
        String releaseId,
        String snowGroup,
        String application,
        String agent,
        String site,
        String owner,
        Integer estimatedRemainingMinutes,
        List<CreateRundownFromTemplateTaskDto> tasks
) {}

package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.contracts.enums.DevelopmentCodeStyle;
import com.wwa.deploymentagent.contracts.enums.DevelopmentProgramType;
import com.wwa.deploymentagent.contracts.enums.DevelopmentSpecStatus;
import com.wwa.deploymentagent.domain.developmentspec.DevelopmentSpec;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

public record DevelopmentSpecDto(
        String id,
        String title,
        String moduleName,
        String programType,
        String codeStyle,
        String application,
        String snowGroup,
        Map<String, Object> sourcePayload,
        Map<String, Object> generatedPayload,
        String generatedContent,
        Instant generatedAt,
        String generatedBy,
        DevelopmentSpecStatus status,
        String createdBy,
        Instant createdAt,
        String updatedBy,
        Instant updatedAt,
        Long version
) {
    public static DevelopmentSpecDto from(DevelopmentSpec spec) {
        return new DevelopmentSpecDto(
                spec.getId(),
                spec.getTitle(),
                spec.getModuleName(),
                spec.getProgramType(),
                spec.getCodeStyle(),
                spec.getApplication(),
                spec.getSnowGroup(),
                copyMap(spec.getSourcePayload()),
                copyMap(spec.getGeneratedPayload()),
                spec.getGeneratedContent(),
                spec.getGeneratedAt(),
                spec.getGeneratedBy(),
                spec.getStatus(),
                spec.getCreatedBy(),
                spec.getCreatedAt(),
                spec.getUpdatedBy(),
                spec.getUpdatedAt(),
                spec.getVersion()
        );
    }

    private static Map<String, Object> copyMap(Map<String, Object> source) {
        return source == null ? null : new LinkedHashMap<>(source);
    }

    public record UpsertRequest(
            @NotBlank String title,
            String moduleName,
            @NotNull DevelopmentProgramType programType,
            @NotNull DevelopmentCodeStyle codeStyle,
            @NotBlank String application,
            @NotBlank String snowGroup,
            @NotNull Map<String, Object> sourcePayload,
            Long version
    ) {}
}

package com.wwa.agenthub.contracts.dto;

import com.wwa.agenthub.domain.decision.DecisionType;
import jakarta.validation.constraints.NotNull;

public record DecisionRequestDto(
        @NotNull DecisionType decision,
        String comment
) {}

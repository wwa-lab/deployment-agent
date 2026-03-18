package com.wwa.deploymentagent.contracts.dto;

import com.wwa.deploymentagent.domain.decision.DecisionType;
import jakarta.validation.constraints.NotNull;

public record DecisionRequestDto(
        @NotNull DecisionType decision,
        String comment
) {}

package com.wwa.agenthub.domain.releaseflow;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.enums.FlowStatus;

/**
 * Immutable filter DTO for {@link ReleaseFlowService#listByAgent} queries.
 *
 * <p>Introduced in BA-T12 to replace long positional argument lists on the
 * platform {@code list}/{@code listByAgent} read path.
 */
public record ReleaseFlowFilter(
        String projectId,
        FlowStatus flowStatus,
        String stage,
        String application,
        String snowGroup,
        UserContext user,
        boolean includeArchived
) {
    public static ReleaseFlowFilter empty() {
        return new ReleaseFlowFilter(null, null, null, null, null, null, false);
    }

    public ReleaseFlowFilter withUser(UserContext user) {
        return new ReleaseFlowFilter(projectId, flowStatus, stage, application, snowGroup, user, includeArchived);
    }
}

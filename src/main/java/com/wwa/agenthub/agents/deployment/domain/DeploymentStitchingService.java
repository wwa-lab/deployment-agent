package com.wwa.agenthub.agents.deployment.domain;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.ReleaseFlowDetailDto;
import com.wwa.agenthub.contracts.dto.ReleaseFlowListItemDto;
import com.wwa.agenthub.contracts.enums.FlowStatus;
import com.wwa.agenthub.domain.releaseflow.ReleaseFlowService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Deployment Agent stitching facade.
 *
 * <p>Owns SIT/UAT/PROD release-family stitching logic specific to the Deployment Agent.
 * During Phase D of the Build Agent refactor this class is a thin delegate to
 * {@link ReleaseFlowService}; in BA-T12 the stitching method bodies will be inlined here
 * and removed from {@link ReleaseFlowService}, completing PL-5.
 */
@Service
@RequiredArgsConstructor
public class DeploymentStitchingService {

    private final ReleaseFlowService releaseFlowService;

    @Transactional(readOnly = true)
    public Page<ReleaseFlowListItemDto> listStitchedSummaries(String projectId,
                                                              FlowStatus flowStatus,
                                                              String stage,
                                                              String application,
                                                              String snowGroup,
                                                              String agent,
                                                              UserContext user,
                                                              String attemptView,
                                                              Pageable pageable,
                                                              boolean includeArchived) {
        return releaseFlowService.listStitchedSummaries(
                projectId,
                flowStatus,
                stage,
                application,
                snowGroup,
                agent,
                user,
                attemptView,
                pageable,
                includeArchived);
    }

    @Transactional(readOnly = true)
    public ReleaseFlowDetailDto getStitchedDetail(String releaseFlowId,
                                                  List<String> linkedFlowIds,
                                                  boolean includeArchived,
                                                  UserContext user) {
        return releaseFlowService.getStitchedDetail(releaseFlowId, linkedFlowIds, includeArchived, user);
    }
}

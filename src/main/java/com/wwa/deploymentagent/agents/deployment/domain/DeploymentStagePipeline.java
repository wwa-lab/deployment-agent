package com.wwa.deploymentagent.agents.deployment.domain;

import com.wwa.deploymentagent.contracts.AgentId;
import com.wwa.deploymentagent.platform.domain.StagePipeline;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class DeploymentStagePipeline implements StagePipeline {

    private static final List<String> STAGES = List.of("SIT", "UAT", "PROD");

    @Override
    public String agentId() {
        return AgentId.DEPLOYMENT_AGENT;
    }

    @Override
    public Optional<String> next(String currentStage) {
        int idx = indexOf(currentStage);
        return idx + 1 < STAGES.size()
                ? Optional.of(STAGES.get(idx + 1))
                : Optional.empty();
    }

    @Override
    public boolean isTerminal(String stage) {
        int idx = indexOf(stage);
        return idx == STAGES.size() - 1;
    }

    @Override
    public List<String> orderedStages() {
        return STAGES;
    }

    private int indexOf(String stage) {
        int idx = STAGES.indexOf(stage);
        if (idx < 0) {
            throw new IllegalArgumentException(
                    "Stage '" + stage + "' is not declared in DeploymentStagePipeline. "
                            + "Valid stages: " + STAGES);
        }
        return idx;
    }
}

package com.wwa.deploymentagent.agents.build.domain;

import com.wwa.deploymentagent.contracts.AgentId;
import com.wwa.deploymentagent.platform.domain.StagePipeline;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Optional;

@Component
public class BuildStagePipeline implements StagePipeline {

    private static final List<String> STAGES = List.of("DEV");

    @Override
    public String agentId() {
        return AgentId.BUILD_AGENT;
    }

    @Override
    public Optional<String> next(String currentStage) {
        indexOf(currentStage);
        return Optional.empty();
    }

    @Override
    public boolean isTerminal(String stage) {
        indexOf(stage);
        return true;
    }

    @Override
    public List<String> orderedStages() {
        return STAGES;
    }

    private int indexOf(String stage) {
        int idx = STAGES.indexOf(stage);
        if (idx < 0) {
            throw new IllegalArgumentException(
                    "Stage '" + stage + "' is not declared in BuildStagePipeline. "
                            + "Valid stages: " + STAGES);
        }
        return idx;
    }
}

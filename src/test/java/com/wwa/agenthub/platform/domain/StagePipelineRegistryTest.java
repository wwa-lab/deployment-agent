package com.wwa.agenthub.platform.domain;

import com.wwa.agenthub.agents.build.domain.BuildStagePipeline;
import com.wwa.agenthub.agents.deployment.domain.DeploymentStagePipeline;
import com.wwa.agenthub.agents.testing.domain.TestingStagePipeline;
import com.wwa.agenthub.contracts.AgentId;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StagePipelineRegistryTest {

    private final StagePipelineRegistry registry = new StagePipelineRegistry(
            List.of(new DeploymentStagePipeline(), new TestingStagePipeline(), new BuildStagePipeline())
    );

    @Test
    void forAgent_deployment_returnsDeploymentPipeline() {
        StagePipeline pipeline = registry.forAgent(AgentId.DEPLOYMENT_AGENT);
        assertThat(pipeline).isInstanceOf(DeploymentStagePipeline.class);
    }

    @Test
    void forAgent_build_returnsBuildPipeline() {
        StagePipeline pipeline = registry.forAgent(AgentId.BUILD_AGENT);
        assertThat(pipeline).isInstanceOf(BuildStagePipeline.class);
    }

    @Test
    void forAgent_unknown_throwsIllegalState() {
        assertThatThrownBy(() -> registry.forAgent("totally-unknown"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("totally-unknown");
    }

    @Test
    void duplicateAgentId_throwsAtConstruction() {
        assertThatThrownBy(() -> new StagePipelineRegistry(
                List.of(new DeploymentStagePipeline(), new DeploymentStagePipeline())
        )).isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Duplicate");
    }
}

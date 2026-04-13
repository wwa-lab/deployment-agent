package com.wwa.agenthub.agents.deployment.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DeploymentStageTest {

    @Test
    void declaredValues() {
        assertThat(DeploymentStage.values())
                .containsExactly(DeploymentStage.SIT, DeploymentStage.UAT, DeploymentStage.PROD);
    }

    @Test
    void fromStringRoundTrip() {
        for (DeploymentStage stage : DeploymentStage.values()) {
            assertThat(DeploymentStage.fromString(stage.name())).isEqualTo(stage);
        }
    }

    @Test
    void fromStringUnknownThrows() {
        assertThatThrownBy(() -> DeploymentStage.fromString("UNKNOWN"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

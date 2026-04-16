package com.wwa.agenthub.agents.build.domain;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BuildStageTest {

    @Test
    void declaredValues() {
        assertThat(BuildStage.values()).containsExactly(BuildStage.DEV);
    }

    @Test
    void fromStringRoundTrip() {
        assertThat(BuildStage.fromString("DEV")).isEqualTo(BuildStage.DEV);
    }

    @Test
    void fromStringUnknownThrows() {
        assertThatThrownBy(() -> BuildStage.fromString("SIT"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}

package com.wwa.agenthub.platform.domain;

import com.wwa.agenthub.agents.build.domain.BuildStagePipeline;
import com.wwa.agenthub.agents.deployment.domain.DeploymentStagePipeline;
import com.wwa.agenthub.agents.testing.domain.TestingStagePipeline;
import com.wwa.agenthub.contracts.AgentId;
import org.junit.jupiter.api.Named;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StagePipelineContractTest {

    static Stream<Named<StagePipeline>> pipelines() {
        return Stream.of(
                Named.of("DeploymentStagePipeline", new DeploymentStagePipeline()),
                Named.of("TestingStagePipeline", new TestingStagePipeline()),
                Named.of("BuildStagePipeline", new BuildStagePipeline())
        );
    }

    // Case 1 & 2: next(firstStage) — depends on pipeline size
    @ParameterizedTest
    @MethodSource("pipelines")
    void next_firstStage(StagePipeline pipeline) {
        List<String> stages = pipeline.orderedStages();
        String first = stages.getFirst();
        Optional<String> result = pipeline.next(first);
        if (stages.size() >= 2) {
            assertThat(result).contains(stages.get(1));
        } else {
            assertThat(result).isEmpty();
        }
    }

    // Case 3: next(lastStage) returns empty
    @ParameterizedTest
    @MethodSource("pipelines")
    void next_lastStage_returnsEmpty(StagePipeline pipeline) {
        String last = pipeline.orderedStages().getLast();
        assertThat(pipeline.next(last)).isEmpty();
    }

    // Case 4: next("totally-unknown") throws IllegalArgumentException
    @ParameterizedTest
    @MethodSource("pipelines")
    void next_unknownStage_throwsIllegalArgument(StagePipeline pipeline) {
        assertThatThrownBy(() -> pipeline.next("totally-unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Case 5: next("") throws
    @ParameterizedTest
    @MethodSource("pipelines")
    void next_emptyString_throws(StagePipeline pipeline) {
        assertThatThrownBy(() -> pipeline.next(""))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Case 6: next(null) throws
    @ParameterizedTest
    @MethodSource("pipelines")
    void next_null_throws(StagePipeline pipeline) {
        assertThatThrownBy(() -> pipeline.next(null))
                .isInstanceOf(RuntimeException.class);
    }

    // Case 7: isTerminal(lastStage) returns true
    @ParameterizedTest
    @MethodSource("pipelines")
    void isTerminal_lastStage_returnsTrue(StagePipeline pipeline) {
        String last = pipeline.orderedStages().getLast();
        assertThat(pipeline.isTerminal(last)).isTrue();
    }

    // Case 8: isTerminal(firstStage) for multi-stage pipelines
    @ParameterizedTest
    @MethodSource("pipelines")
    void isTerminal_firstStage_returnsFalseForMultiStage(StagePipeline pipeline) {
        List<String> stages = pipeline.orderedStages();
        if (stages.size() >= 2) {
            assertThat(pipeline.isTerminal(stages.getFirst())).isFalse();
        }
    }

    // Case 9: isTerminal("totally-unknown") throws
    @ParameterizedTest
    @MethodSource("pipelines")
    void isTerminal_unknownStage_throws(StagePipeline pipeline) {
        assertThatThrownBy(() -> pipeline.isTerminal("totally-unknown"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // Case 10: orderedStages() non-empty, immutable, no duplicates
    @ParameterizedTest
    @MethodSource("pipelines")
    void orderedStages_nonEmpty_immutable_noDuplicates(StagePipeline pipeline) {
        List<String> stages = pipeline.orderedStages();
        assertThat(stages).isNotEmpty();
        assertThat(new HashSet<>(stages)).hasSameSizeAs(stages);
        assertThatThrownBy(() -> stages.add("BOGUS"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    // Case 11: agentId() non-null, non-blank
    @ParameterizedTest
    @MethodSource("pipelines")
    void agentId_nonNullNonBlank(StagePipeline pipeline) {
        assertThat(pipeline.agentId()).isNotNull().isNotBlank();
    }

    // Specific agentId assertions
    @ParameterizedTest
    @MethodSource("pipelines")
    void agentId_returnsExpectedConstant(StagePipeline pipeline) {
        String expected = switch (pipeline.getClass().getSimpleName()) {
            case "DeploymentStagePipeline" -> AgentId.DEPLOYMENT_AGENT;
            case "TestingStagePipeline" -> AgentId.TESTING_AGENT;
            case "BuildStagePipeline" -> AgentId.BUILD_AGENT;
            default -> throw new IllegalStateException("Unknown pipeline: " + pipeline.getClass());
        };
        assertThat(pipeline.agentId()).isEqualTo(expected);
    }

    // Deployment-specific ordering
    @ParameterizedTest
    @MethodSource("pipelines")
    void deployment_ordering(StagePipeline pipeline) {
        if (pipeline instanceof DeploymentStagePipeline) {
            assertThat(pipeline.orderedStages()).containsExactly("SIT", "UAT", "PROD");
            assertThat(pipeline.next("SIT")).contains("UAT");
            assertThat(pipeline.next("UAT")).contains("PROD");
            assertThat(pipeline.next("PROD")).isEmpty();
        }
    }
}

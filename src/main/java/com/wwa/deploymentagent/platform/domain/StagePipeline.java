package com.wwa.deploymentagent.platform.domain;

import java.util.List;
import java.util.Optional;

public interface StagePipeline {

    /**
     * The agent ID this pipeline belongs to. Used by {@link StagePipelineRegistry}
     * to build the agent -> pipeline map at application startup. Must be a stable
     * string constant drawn from {@code AgentId}.
     */
    String agentId();

    /**
     * Returns the next stage after {@code currentStage}, or {@link Optional#empty()}
     * if {@code currentStage} is the terminal stage in this pipeline.
     *
     * @throws IllegalArgumentException if {@code currentStage} is not a declared
     *         member of this pipeline's {@link #orderedStages()}. Fail-loud
     *         intentional: a mis-routed progression call (e.g. passing a "SIT" flow
     *         through {@code BuildStagePipeline}) MUST crash visibly rather than
     *         silently be treated as terminal.
     */
    Optional<String> next(String currentStage);

    /**
     * True if {@code stage} is terminal (has no successor) in this pipeline.
     *
     * @throws IllegalArgumentException if {@code stage} is not a declared member
     *         of this pipeline's {@link #orderedStages()}. Same fail-loud
     *         rationale as {@link #next(String)}.
     */
    boolean isTerminal(String stage);

    /** All stages owned by this pipeline, in declared order. Non-empty, no duplicates. */
    List<String> orderedStages();
}

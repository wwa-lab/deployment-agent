package com.wwa.agenthub.platform.domain;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class StagePipelineRegistry {

    private final Map<String, StagePipeline> byAgent;

    public StagePipelineRegistry(List<StagePipeline> pipelines) {
        this.byAgent = pipelines.stream()
                .collect(Collectors.toUnmodifiableMap(
                        StagePipeline::agentId,
                        p -> p,
                        (a, b) -> {
                            throw new IllegalStateException(
                                    "Duplicate StagePipeline for agentId " + a.agentId()
                                            + ": " + a.getClass().getName()
                                            + " and " + b.getClass().getName());
                        }));
    }

    public StagePipeline forAgent(String agentId) {
        StagePipeline pipeline = byAgent.get(agentId);
        if (pipeline == null) {
            throw new IllegalStateException(
                    "No StagePipeline registered for agentId: " + agentId);
        }
        return pipeline;
    }
}

package com.wwa.agenthub.platform.domain.integration.review;

import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface IntegrationReviewDecisionRepository
        extends JpaRepository<IntegrationReviewDecision, String> {

    @EntityGraph(attributePaths = {"task", "execution"})
    Optional<IntegrationReviewDecision> findByExecutionId(String executionId);
}

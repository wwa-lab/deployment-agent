package com.wwa.deploymentagent.domain.releaseflow;

import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import com.wwa.deploymentagent.contracts.enums.Stage;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ReleaseFlowRepository extends JpaRepository<ReleaseFlow, String> {

    /**
     * Look up an existing Release Flow by grouping key: (projectId, normalizedReleaseId).
     * Used during import to determine whether to create a new RF or attach to existing one.
     */
    Optional<ReleaseFlow> findByProjectIdAndNormalizedReleaseId(String projectId, String normalizedReleaseId);

    // ─── Filtered paginated queries ──────────────────────────────────────────

    Page<ReleaseFlow> findByProjectId(String projectId, Pageable pageable);

    Page<ReleaseFlow> findByFlowStatus(FlowStatus flowStatus, Pageable pageable);

    Page<ReleaseFlow> findByCurrentStage(Stage currentStage, Pageable pageable);

    Page<ReleaseFlow> findByProjectIdAndFlowStatus(String projectId, FlowStatus flowStatus, Pageable pageable);

    Page<ReleaseFlow> findByProjectIdAndCurrentStage(String projectId, Stage currentStage, Pageable pageable);

    Page<ReleaseFlow> findByFlowStatusAndCurrentStage(FlowStatus flowStatus, Stage currentStage, Pageable pageable);

    Page<ReleaseFlow> findByProjectIdAndFlowStatusAndCurrentStage(
            String projectId, FlowStatus flowStatus, Stage currentStage, Pageable pageable);

    /** Look up the first (active) Release Flow for a given projectId – used during import. */
    Optional<ReleaseFlow> findFirstByProjectId(String projectId);

    /**
     * Load a Release Flow with its full request+task hierarchy in a single query.
     * Uses DISTINCT to avoid duplicates from multiple JOIN FETCH paths.
     */
    @Query("SELECT DISTINCT rf FROM ReleaseFlow rf " +
           "LEFT JOIN FETCH rf.requests " +
           "WHERE rf.id = :id")
    Optional<ReleaseFlow> findByIdWithFullHierarchy(@Param("id") String id);

    /** Count of existing Release Flows for a given projectId – used for seq generation. */
    @Query("SELECT COUNT(rf) FROM ReleaseFlow rf WHERE rf.projectId = :projectId")
    long countByProjectId(@Param("projectId") String projectId);
}

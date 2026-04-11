package com.wwa.deploymentagent.domain.releaseflow;

import com.wwa.deploymentagent.contracts.enums.FlowStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.List;

@Repository
public interface ReleaseFlowRepository extends JpaRepository<ReleaseFlow, String> {

    /**
     * Look up an existing Release Flow by grouping key: (projectId, normalizedReleaseId).
     * Used during import to determine whether to create a new RF or attach to existing one.
     */
    Optional<ReleaseFlow> findByProjectIdAndNormalizedReleaseIdAndArchivedAtIsNull(
            String projectId, String normalizedReleaseId);

    Optional<ReleaseFlow> findByProjectIdAndNormalizedReleaseId(
            String projectId, String normalizedReleaseId);

    @Query("""
            SELECT rf FROM ReleaseFlow rf
            WHERE (:projectId IS NULL OR rf.projectId = :projectId)
              AND (:flowStatus IS NULL OR rf.flowStatus = :flowStatus)
              AND (:stage IS NULL OR rf.currentStage = :stage)
              AND (:includeArchived = TRUE OR rf.archivedAt IS NULL)
            """)
    Page<ReleaseFlow> search(@Param("projectId") String projectId,
                             @Param("flowStatus") FlowStatus flowStatus,
                             @Param("stage") String stage,
                             @Param("includeArchived") boolean includeArchived,
                             Pageable pageable);

    /** Look up the first (active) Release Flow for a given projectId – used during import. */
    Optional<ReleaseFlow> findFirstByProjectIdAndArchivedAtIsNullOrderByCreatedAtDesc(String projectId);

    List<ReleaseFlow> findByProjectIdAndArchivedAtIsNullOrderByCreatedAtDesc(String projectId);

    @Query("""
            SELECT rf FROM ReleaseFlow rf
            WHERE rf.projectId = :projectId
              AND rf.archivedAt IS NULL
              AND NOT EXISTS (
                  SELECT 1 FROM Request r
                  WHERE r.releaseFlow = rf
                    AND r.stage = :stage
                    AND r.archivedAt IS NULL
              )
            ORDER BY rf.createdAt DESC
            """)
    List<ReleaseFlow> findActiveByProjectIdWithoutStageOrderByCreatedAtDesc(@Param("projectId") String projectId,
                                                                             @Param("stage") String stage);

    default Optional<ReleaseFlow> findFirstByProjectId(String projectId) {
        return findFirstByProjectIdAndArchivedAtIsNullOrderByCreatedAtDesc(projectId);
    }

    Optional<ReleaseFlow> findByIdAndArchivedAtIsNull(String id);

    /**
     * Load a Release Flow with its full request+task hierarchy in a single query.
     * Uses DISTINCT to avoid duplicates from multiple JOIN FETCH paths.
     */
    /** Count of existing Release Flows for a given projectId – used for seq generation. */
    @Query("SELECT COUNT(rf) FROM ReleaseFlow rf WHERE rf.projectId = :projectId")
    long countByProjectId(@Param("projectId") String projectId);
}

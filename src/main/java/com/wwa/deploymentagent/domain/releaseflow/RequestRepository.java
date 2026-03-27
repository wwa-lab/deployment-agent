package com.wwa.deploymentagent.domain.releaseflow;

import com.wwa.deploymentagent.contracts.enums.Stage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface RequestRepository extends JpaRepository<Request, String> {

    /**
     * Fetch all Requests for a Release Flow, eagerly loading their Tasks.
     * Used by aggregation and detail view.
     */
    @Query("""
            SELECT DISTINCT r FROM Request r
            LEFT JOIN FETCH r.tasks
            WHERE r.releaseFlow.id = :releaseFlowId
              AND (:includeArchived = TRUE OR r.archivedAt IS NULL)
            """)
    List<Request> findByReleaseFlowIdWithTasks(@Param("releaseFlowId") String releaseFlowId,
                                               @Param("includeArchived") boolean includeArchived);

    /** Fetch all Requests for a Release Flow (no task loading). */
    List<Request> findByReleaseFlowIdAndArchivedAtIsNull(String releaseFlowId);

    default List<Request> findByReleaseFlowId(String releaseFlowId) {
        return findByReleaseFlowIdAndArchivedAtIsNull(releaseFlowId);
    }

    @Query("""
            SELECT r FROM Request r
            WHERE r.releaseFlow.id IN :releaseFlowIds
              AND (:includeArchived = TRUE OR r.archivedAt IS NULL)
            """)
    List<Request> findByReleaseFlowIds(@Param("releaseFlowIds") List<String> releaseFlowIds,
                                       @Param("includeArchived") boolean includeArchived);

    Optional<Request> findTopByReleaseFlowIdAndStageAndArchivedAtIsNullOrderByAttemptNumberDescUpdatedAtDesc(
            String releaseFlowId, Stage stage);

    Optional<Request> findTopByReleaseFlowIdAndStageOrderByAttemptNumberDescUpdatedAtDesc(
            String releaseFlowId, Stage stage);

    @Query("""
            SELECT COALESCE(MAX(r.attemptNumber), 0) FROM Request r
            WHERE r.releaseFlow.id = :releaseFlowId
              AND r.stage = :stage
            """)
    int findMaxAttemptNumberByReleaseFlowIdAndStage(@Param("releaseFlowId") String releaseFlowId,
                                                     @Param("stage") Stage stage);

    Optional<Request> findByIdAndReleaseFlowId(String id, String releaseFlowId);

    @Query("""
            SELECT DISTINCT r FROM Request r
            LEFT JOIN FETCH r.tasks
            WHERE r.id = :requestId
              AND r.releaseFlow.id = :releaseFlowId
              AND r.archivedAt IS NULL
            """)
    Optional<Request> findActiveByIdAndReleaseFlowIdWithTasks(@Param("requestId") String requestId,
                                                              @Param("releaseFlowId") String releaseFlowId);

    @Query("""
            SELECT DISTINCT r FROM Request r
            LEFT JOIN FETCH r.tasks
            WHERE r.id = :requestId
              AND r.releaseFlow.id = :releaseFlowId
            """)
    Optional<Request> findByIdAndReleaseFlowIdWithTasks(@Param("requestId") String requestId,
                                                        @Param("releaseFlowId") String releaseFlowId);
}

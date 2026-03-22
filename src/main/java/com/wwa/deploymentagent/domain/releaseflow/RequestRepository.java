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
    @Query("SELECT r FROM Request r LEFT JOIN FETCH r.tasks WHERE r.releaseFlow.id = :releaseFlowId")
    List<Request> findByReleaseFlowIdWithTasks(@Param("releaseFlowId") String releaseFlowId);

    /** Fetch all Requests for a Release Flow (no task loading). */
    List<Request> findByReleaseFlowId(String releaseFlowId);

    /** Find existing Request for a specific release flow and stage – used during import upsert. */
    Optional<Request> findByReleaseFlowIdAndStage(String releaseFlowId, Stage stage);

    Optional<Request> findByIdAndReleaseFlowId(String id, String releaseFlowId);

    @Query("SELECT r FROM Request r LEFT JOIN FETCH r.tasks WHERE r.id = :requestId AND r.releaseFlow.id = :releaseFlowId")
    Optional<Request> findByIdAndReleaseFlowIdWithTasks(@Param("requestId") String requestId,
                                                        @Param("releaseFlowId") String releaseFlowId);
}

package com.wwa.agenthub.platform.domain.integration.artifact;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface TaskInputArtifactApprovalRepository
        extends JpaRepository<TaskInputArtifactApproval, String> {

    @Query("""
        SELECT approval.artifact.id
        FROM TaskInputArtifactApproval approval
        WHERE approval.task.id = :taskId
        ORDER BY approval.approvedAt ASC, approval.id ASC
        """)
    List<String> findArtifactIdsByTaskId(@Param("taskId") String taskId);

    Optional<TaskInputArtifactApproval> findByTaskIdAndArtifactId(String taskId, String artifactId);

    long countByTaskId(String taskId);

    @Query("""
        SELECT approval.task.id AS taskId, approval.artifact.id AS artifactId
        FROM TaskInputArtifactApproval approval
        WHERE approval.task.id IN :taskIds
        ORDER BY approval.approvedAt ASC, approval.id ASC
        """)
    List<ApprovedArtifactId> findArtifactIdsByTaskIds(@Param("taskIds") Set<String> taskIds);

    interface ApprovedArtifactId {
        String getTaskId();
        String getArtifactId();
    }
}

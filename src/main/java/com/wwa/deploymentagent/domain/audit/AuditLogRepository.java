package com.wwa.deploymentagent.domain.audit;

import com.wwa.deploymentagent.contracts.enums.AuditActionType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLogEntry, String> {

    Page<AuditLogEntry> findByOperatorId(String operatorId, Pageable pageable);

    Page<AuditLogEntry> findByActionType(AuditActionType actionType, Pageable pageable);

    Page<AuditLogEntry> findByReleaseFlowId(String releaseFlowId, Pageable pageable);

    Page<AuditLogEntry> findByTaskId(String taskId, Pageable pageable);
}

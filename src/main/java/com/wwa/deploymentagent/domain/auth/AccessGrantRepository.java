package com.wwa.deploymentagent.domain.auth;

import com.wwa.deploymentagent.contracts.enums.AccessGrantStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface AccessGrantRepository extends JpaRepository<AccessGrant, String> {

    @Query("""
            SELECT ag FROM AccessGrant ag
            WHERE (:status IS NULL OR ag.grantStatus = :status)
              AND (
                    :query IS NULL
                    OR LOWER(ag.employeeId) LIKE LOWER(CONCAT(CONCAT('%', :query), '%'))
                    OR LOWER(ag.displayNameSnapshot) LIKE LOWER(CONCAT(CONCAT('%', :query), '%'))
                  )
            """)
    Page<AccessGrant> search(@Param("query") String query,
                             @Param("status") AccessGrantStatus status,
                             Pageable pageable);
}

package com.wwa.deploymentagent.domain.developmentspec;

import com.wwa.deploymentagent.contracts.enums.DevelopmentSpecStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DevelopmentSpecRepository extends JpaRepository<DevelopmentSpec, String> {

    @Query("""
            SELECT ds FROM DevelopmentSpec ds
            WHERE (:status IS NULL OR ds.status = :status)
              AND (
                    :query IS NULL
                    OR LOWER(ds.title) LIKE LOWER(CONCAT(CONCAT('%', :query), '%'))
                    OR LOWER(ds.moduleName) LIKE LOWER(CONCAT(CONCAT('%', :query), '%'))
                  )
            """)
    Page<DevelopmentSpec> search(@Param("query") String query,
                                 @Param("status") DevelopmentSpecStatus status,
                                 Pageable pageable);
}

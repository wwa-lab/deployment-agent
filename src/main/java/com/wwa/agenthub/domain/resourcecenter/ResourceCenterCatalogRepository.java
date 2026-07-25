package com.wwa.agenthub.domain.resourcecenter;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ResourceCenterCatalogRepository extends JpaRepository<ResourceCenterCatalogEntity, String> {

    Optional<ResourceCenterCatalogEntity> findFirstByOrderByIdAsc();
}

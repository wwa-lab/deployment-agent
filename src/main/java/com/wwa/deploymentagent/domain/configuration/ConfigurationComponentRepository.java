package com.wwa.deploymentagent.domain.configuration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ConfigurationComponentRepository extends JpaRepository<ConfigurationComponent, String> {
    List<ConfigurationComponent> findByComponentIdIgnoreCase(String componentId);

    Optional<ConfigurationComponent> findByComponentIdIgnoreCaseAndScopeKey(String componentId, String scopeKey);
}

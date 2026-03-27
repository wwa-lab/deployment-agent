package com.wwa.deploymentagent.domain.configuration;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigurationComponentRepository extends JpaRepository<ConfigurationComponent, String> {
}

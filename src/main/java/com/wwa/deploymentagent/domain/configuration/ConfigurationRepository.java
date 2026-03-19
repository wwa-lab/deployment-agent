package com.wwa.deploymentagent.domain.configuration;

import com.wwa.deploymentagent.contracts.enums.ConfigKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ConfigurationRepository extends JpaRepository<ConfigurationItem, ConfigKey> {
}

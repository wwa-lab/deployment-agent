package com.wwa.agenthub.domain.configuration;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ScopeDirectoryRepository extends JpaRepository<ScopeDirectoryEntry, String> {

    Optional<ScopeDirectoryEntry> findByScopeKey(String scopeKey);
}

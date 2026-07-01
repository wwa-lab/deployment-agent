package com.wwa.agenthub.domain.skillhub;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SkillHubSkillVersionRepository extends JpaRepository<SkillHubSkillVersion, String> {
    List<SkillHubSkillVersion> findBySkillIdOrderByCreatedAtDesc(String skillId);

    Optional<SkillHubSkillVersion> findByIdAndSkillId(String id, String skillId);
}

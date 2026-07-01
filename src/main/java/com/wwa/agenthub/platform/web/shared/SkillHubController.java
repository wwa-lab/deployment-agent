package com.wwa.agenthub.platform.web.shared;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.PaginatedResponseDto;
import com.wwa.agenthub.contracts.dto.SkillHubSkillDto;
import com.wwa.agenthub.contracts.enums.SkillStatus;
import com.wwa.agenthub.domain.skillhub.SkillHubService;
import com.wwa.agenthub.domain.skillhub.SkillHubSkill;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/platform/skill-hub")
@RequiredArgsConstructor
public class SkillHubController {

    private final SkillHubService skillHubService;

    @GetMapping("/skills")
    public ResponseEntity<PaginatedResponseDto<SkillHubSkillDto>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) SkillStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        Page<SkillHubSkill> result = skillHubService.list(
                query,
                category,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt"))
        );
        List<SkillHubSkillDto> data = result.getContent().stream()
                .map(SkillHubSkillDto::from)
                .toList();
        return ResponseEntity.ok(new PaginatedResponseDto<>(
                data,
                result.getTotalElements(),
                result.getNumber(),
                result.getSize()
        ));
    }

    @GetMapping("/skills/{id}")
    public ResponseEntity<SkillHubSkillDto> get(@PathVariable String id) {
        return ResponseEntity.ok(skillHubService.getDetail(id));
    }

    @PostMapping("/skills")
    public ResponseEntity<SkillHubSkillDto> create(
            @Valid @RequestBody SkillHubSkillDto.UpsertRequest body,
            @AuthenticationPrincipal UserContext user
    ) {
        return ResponseEntity.ok(SkillHubSkillDto.from(skillHubService.create(body, user)));
    }

    @PutMapping("/skills/{id}")
    public ResponseEntity<SkillHubSkillDto> update(
            @PathVariable String id,
            @Valid @RequestBody SkillHubSkillDto.UpsertRequest body,
            @AuthenticationPrincipal UserContext user
    ) {
        return ResponseEntity.ok(SkillHubSkillDto.from(skillHubService.update(id, body, user)));
    }

    @PostMapping("/skills/{id}/versions")
    public ResponseEntity<SkillHubSkillDto.VersionDetail> createVersion(
            @PathVariable String id,
            @Valid @RequestBody SkillHubSkillDto.VersionCreateRequest body,
            @AuthenticationPrincipal UserContext user
    ) {
        return ResponseEntity.ok(SkillHubSkillDto.VersionDetail.from(skillHubService.createVersion(id, body, user)));
    }

    @GetMapping("/skills/{id}/versions/{versionId}")
    public ResponseEntity<SkillHubSkillDto.VersionDetail> getVersion(
            @PathVariable String id,
            @PathVariable String versionId
    ) {
        return ResponseEntity.ok(SkillHubSkillDto.VersionDetail.from(skillHubService.getVersion(id, versionId)));
    }
}

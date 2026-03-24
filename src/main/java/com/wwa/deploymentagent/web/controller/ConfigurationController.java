package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.ConfigurationItemDto;
import com.wwa.deploymentagent.domain.configuration.ConfigurationService;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Configuration controller.
 *
 * <pre>
 *   GET  /api/deployment-agent/config  – retrieve all config items
 *   POST /api/deployment-agent/config  – create/update a config item (DevOps Admin only)
 * </pre>
 */
@RestController
@RequestMapping("/api/deployment-agent/config")
@RequiredArgsConstructor
public class ConfigurationController {

    private final ConfigurationService configurationService;

    @GetMapping
    public ResponseEntity<List<ConfigurationItemDto>> listAll() {
        List<ConfigurationItemDto> dtos = configurationService.listAll().stream()
                .map(ConfigurationItemDto::from)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping
    public ResponseEntity<ConfigurationItemDto> upsert(
            @Valid @RequestBody ConfigurationItemDto.UpsertRequest body,
            @AuthenticationPrincipal UserContext user) {
        if (!user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException("config:update");
        }

        ConfigurationItemDto dto = ConfigurationItemDto.from(
                configurationService.upsert(body.key(), body.value(), body.description(), user));
        return ResponseEntity.ok(dto);
    }
}

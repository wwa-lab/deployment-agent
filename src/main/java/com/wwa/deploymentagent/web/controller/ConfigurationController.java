package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.ConfigurationComponentDto;
import com.wwa.deploymentagent.contracts.dto.ConfigurationItemDto;
import com.wwa.deploymentagent.domain.configuration.ConfigurationComponentService;
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

    private final ConfigurationComponentService configurationComponentService;

    @GetMapping
    public ResponseEntity<List<ConfigurationItemDto>> listAll() {
        return ResponseEntity.ok(configurationComponentService.listDerivedConfigItems());
    }

    @PostMapping
    public ResponseEntity<ConfigurationItemDto> upsert(
            @Valid @RequestBody ConfigurationItemDto.UpsertRequest body,
            @AuthenticationPrincipal UserContext user) {
        if (!user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException("config:update");
        }

        return ResponseEntity.ok(
                configurationComponentService.upsertDerivedConfigItem(body, user));
    }

    @GetMapping("/components")
    public ResponseEntity<List<ConfigurationComponentDto>> listComponents() {
        List<ConfigurationComponentDto> dtos = configurationComponentService.listComponents().stream()
                .map(ConfigurationComponentDto::from)
                .toList();
        return ResponseEntity.ok(dtos);
    }

    @PostMapping("/components")
    public ResponseEntity<ConfigurationComponentDto> upsertComponent(
            @Valid @RequestBody ConfigurationComponentDto.UpsertRequest body,
            @AuthenticationPrincipal UserContext user) {
        if (!user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException("config:update");
        }

        return ResponseEntity.ok(
                ConfigurationComponentDto.from(configurationComponentService.upsertComponent(body, user)));
    }

    @DeleteMapping("/components/{componentInstanceId}")
    public ResponseEntity<Void> deleteComponent(
            @PathVariable String componentInstanceId,
            @AuthenticationPrincipal UserContext user) {
        if (!user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException("config:update");
        }

        configurationComponentService.deleteComponent(componentInstanceId, user);
        return ResponseEntity.noContent().build();
    }
}

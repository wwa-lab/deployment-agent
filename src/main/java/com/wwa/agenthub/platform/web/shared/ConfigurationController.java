package com.wwa.agenthub.platform.web.shared;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.ConfigurationComponentDto;
import com.wwa.agenthub.contracts.dto.ConfigurationItemDto;
import com.wwa.agenthub.contracts.dto.ScopeDirectoryEntryDto;
import com.wwa.agenthub.domain.configuration.ConfigurationComponentService;
import com.wwa.agenthub.domain.configuration.ScopeDirectoryService;
import com.wwa.agenthub.errors.ForbiddenAppException;
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
 *   GET  /api/platform/config  – retrieve all config items
 *   POST /api/platform/config  – create/update a config item (DevOps Admin only)
 * </pre>
 */
@RestController
@RequestMapping("/api/platform/config")
@RequiredArgsConstructor
public class ConfigurationController {

    private final ConfigurationComponentService configurationComponentService;
    private final ScopeDirectoryService scopeDirectoryService;

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

    @GetMapping("/scopes")
    public ResponseEntity<List<ScopeDirectoryEntryDto>> listScopes() {
        return ResponseEntity.ok(scopeDirectoryService.listEntries().stream()
                .map(ScopeDirectoryEntryDto::from)
                .toList());
    }

    @PostMapping("/scopes")
    public ResponseEntity<ScopeDirectoryEntryDto> upsertScope(
            @Valid @RequestBody ScopeDirectoryEntryDto.UpsertRequest body,
            @AuthenticationPrincipal UserContext user) {
        if (!user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException("config:update");
        }

        return ResponseEntity.ok(ScopeDirectoryEntryDto.from(scopeDirectoryService.upsert(body, user)));
    }

    @DeleteMapping("/scopes/{id}")
    public ResponseEntity<Void> deleteScope(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user) {
        if (!user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException("config:update");
        }

        scopeDirectoryService.delete(id, user);
        return ResponseEntity.noContent().build();
    }
}

package com.wwa.agenthub.platform.web.shared;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.DirectoryGroupUpsertRequest;
import com.wwa.agenthub.contracts.dto.DirectoryLinkUpsertRequest;
import com.wwa.agenthub.contracts.dto.DirectoryScopeUpsertRequest;
import com.wwa.agenthub.contracts.dto.ResourceCenterCatalogDto;
import com.wwa.agenthub.domain.resourcecenter.ResourceCenterService;
import com.wwa.agenthub.errors.ForbiddenAppException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/platform/resource-center")
@RequiredArgsConstructor
public class ResourceCenterController {

    private final ResourceCenterService resourceCenterService;

    @GetMapping
    public ResponseEntity<ResourceCenterCatalogDto> read(
            @RequestParam(defaultValue = "false") boolean includeDisabled,
            @AuthenticationPrincipal UserContext user) {
        return ResponseEntity.ok(resourceCenterService.read(includeDisabled, user));
    }

    @PostMapping("/scopes")
    public ResponseEntity<ResourceCenterCatalogDto> createScope(
            @Valid @RequestBody DirectoryScopeUpsertRequest body,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "resource_center:update");
        return ResponseEntity.ok(resourceCenterService.createScope(body, user));
    }

    @PutMapping("/scopes/{scopeKey}")
    public ResponseEntity<ResourceCenterCatalogDto> updateScope(
            @PathVariable String scopeKey,
            @RequestParam long expectedVersion,
            @Valid @RequestBody DirectoryScopeUpsertRequest body,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "resource_center:update");
        return ResponseEntity.ok(resourceCenterService.updateScope(scopeKey, expectedVersion, body, user));
    }

    @DeleteMapping("/scopes/{scopeKey}")
    public ResponseEntity<ResourceCenterCatalogDto> deleteScope(
            @PathVariable String scopeKey,
            @RequestParam long expectedVersion,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "resource_center:delete");
        return ResponseEntity.ok(resourceCenterService.deleteScope(scopeKey, expectedVersion, user));
    }

    @PostMapping("/scopes/{scopeKey}/groups")
    public ResponseEntity<ResourceCenterCatalogDto> createGroup(
            @PathVariable String scopeKey,
            @Valid @RequestBody DirectoryGroupUpsertRequest body,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "resource_center:update");
        return ResponseEntity.ok(resourceCenterService.createGroup(scopeKey, body, user));
    }

    @PutMapping("/scopes/{scopeKey}/groups/{groupKey}")
    public ResponseEntity<ResourceCenterCatalogDto> updateGroup(
            @PathVariable String scopeKey,
            @PathVariable String groupKey,
            @RequestParam long expectedVersion,
            @Valid @RequestBody DirectoryGroupUpsertRequest body,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "resource_center:update");
        return ResponseEntity.ok(
                resourceCenterService.updateGroup(scopeKey, groupKey, expectedVersion, body, user));
    }

    @DeleteMapping("/scopes/{scopeKey}/groups/{groupKey}")
    public ResponseEntity<ResourceCenterCatalogDto> deleteGroup(
            @PathVariable String scopeKey,
            @PathVariable String groupKey,
            @RequestParam long expectedVersion,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "resource_center:delete");
        return ResponseEntity.ok(
                resourceCenterService.deleteGroup(scopeKey, groupKey, expectedVersion, user));
    }

    @PostMapping("/scopes/{scopeKey}/groups/{groupKey}/links")
    public ResponseEntity<ResourceCenterCatalogDto> createLink(
            @PathVariable String scopeKey,
            @PathVariable String groupKey,
            @Valid @RequestBody DirectoryLinkUpsertRequest body,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "resource_center:update");
        return ResponseEntity.ok(resourceCenterService.createLink(scopeKey, groupKey, body, user));
    }

    @PutMapping("/links/{linkId}")
    public ResponseEntity<ResourceCenterCatalogDto> updateLink(
            @PathVariable String linkId,
            @RequestParam long expectedVersion,
            @Valid @RequestBody DirectoryLinkUpsertRequest body,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "resource_center:update");
        return ResponseEntity.ok(resourceCenterService.updateLink(linkId, expectedVersion, body, user));
    }

    @DeleteMapping("/links/{linkId}")
    public ResponseEntity<ResourceCenterCatalogDto> deleteLink(
            @PathVariable String linkId,
            @RequestParam long expectedVersion,
            @AuthenticationPrincipal UserContext user) {
        validateAdmin(user, "resource_center:delete");
        return ResponseEntity.ok(resourceCenterService.deleteLink(linkId, expectedVersion, user));
    }

    private void validateAdmin(UserContext user, String action) {
        if (user == null || !user.hasRole("DEVOPS_ADMIN")) {
            throw new ForbiddenAppException(action);
        }
    }
}

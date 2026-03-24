package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.AccessGrantDto;
import com.wwa.deploymentagent.contracts.dto.PaginatedResponseDto;
import com.wwa.deploymentagent.contracts.enums.AccessGrantStatus;
import com.wwa.deploymentagent.domain.auth.AccessGrant;
import com.wwa.deploymentagent.domain.auth.AccessGrantService;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deployment-agent/access-grants")
@RequiredArgsConstructor
public class AccessGrantController {

    private final AccessGrantService accessGrantService;

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<AccessGrantDto>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) AccessGrantStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserContext user
    ) {
        validateAccessManager(user);

        Page<AccessGrant> result = accessGrantService.list(
                query,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")),
                user
        );

        List<AccessGrantDto> data = result.getContent().stream()
                .map(AccessGrantDto::from)
                .toList();

        return ResponseEntity.ok(new PaginatedResponseDto<>(
                data,
                result.getTotalElements(),
                result.getNumber(),
                result.getSize()
        ));
    }

    @PostMapping
    public ResponseEntity<AccessGrantDto> create(
            @Valid @RequestBody AccessGrantDto.CreateRequest body,
            @AuthenticationPrincipal UserContext user
    ) {
        validateAccessManager(user);
        return ResponseEntity.ok(AccessGrantDto.from(
                accessGrantService.createGrant(
                        body.employeeId(),
                        body.grantStatus(),
                        body.assignedRoles(),
                        body.scopeGrants(),
                        body.note(),
                        user)
        ));
    }

    @PatchMapping("/{employeeId}")
    public ResponseEntity<AccessGrantDto> update(
            @PathVariable String employeeId,
            @Valid @RequestBody AccessGrantDto.UpdateRequest body,
            @AuthenticationPrincipal UserContext user
    ) {
        validateAccessManager(user);
        return ResponseEntity.ok(AccessGrantDto.from(
                accessGrantService.updateGrant(
                        employeeId,
                        body.assignedRoles(),
                        body.scopeGrants(),
                        body.note(),
                        user)
        ));
    }

    @PostMapping("/{employeeId}/suspend")
    public ResponseEntity<AccessGrantDto> suspend(
            @PathVariable String employeeId,
            @RequestBody(required = false) AccessGrantDto.SuspendRequest body,
            @AuthenticationPrincipal UserContext user
    ) {
        validateAccessManager(user);
        return ResponseEntity.ok(AccessGrantDto.from(
                accessGrantService.suspendGrant(employeeId, body == null ? null : body.note(), user)
        ));
    }

    @PostMapping("/{employeeId}/reactivate")
    public ResponseEntity<AccessGrantDto> reactivate(
            @PathVariable String employeeId,
            @RequestBody(required = false) AccessGrantDto.ReactivateRequest body,
            @AuthenticationPrincipal UserContext user
    ) {
        validateAccessManager(user);
        return ResponseEntity.ok(AccessGrantDto.from(
                accessGrantService.reactivateGrant(
                        employeeId,
                        body == null ? null : body.assignedRoles(),
                        body == null ? null : body.scopeGrants(),
                        body == null ? null : body.note(),
                        user
                )
        ));
    }

    private void validateAccessManager(UserContext user) {
        if (user == null || (!user.hasPermission("access.manage") && !user.hasRole("DEVOPS_ADMIN"))) {
            throw new ForbiddenAppException("access-manage");
        }
    }
}

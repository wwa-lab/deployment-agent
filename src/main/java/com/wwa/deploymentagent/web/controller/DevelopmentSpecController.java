package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.DevelopmentSpecDto;
import com.wwa.deploymentagent.contracts.dto.PaginatedResponseDto;
import com.wwa.deploymentagent.contracts.enums.DevelopmentSpecStatus;
import com.wwa.deploymentagent.domain.developmentspec.DevelopmentSpec;
import com.wwa.deploymentagent.domain.developmentspec.DevelopmentSpecService;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/deployment-agent/development-specs")
@RequiredArgsConstructor
public class DevelopmentSpecController {

    private final DevelopmentSpecService developmentSpecService;

    @GetMapping
    public ResponseEntity<PaginatedResponseDto<DevelopmentSpecDto>> list(
            @RequestParam(required = false) String query,
            @RequestParam(required = false) DevelopmentSpecStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal UserContext user
    ) {
        validateReader(user);

        Page<DevelopmentSpec> result = developmentSpecService.list(
                query,
                status,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "updatedAt")),
                user
        );

        List<DevelopmentSpecDto> data = result.getContent().stream()
                .map(DevelopmentSpecDto::from)
                .toList();

        return ResponseEntity.ok(new PaginatedResponseDto<>(
                data,
                result.getTotalElements(),
                result.getNumber(),
                result.getSize()
        ));
    }

    @GetMapping("/{id}")
    public ResponseEntity<DevelopmentSpecDto> getById(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user
    ) {
        validateReader(user);
        return ResponseEntity.ok(DevelopmentSpecDto.from(developmentSpecService.get(id, user)));
    }

    @GetMapping("/{id}/export")
    public ResponseEntity<byte[]> export(
            @PathVariable String id,
            @RequestParam(defaultValue = "markdown") String format,
            @AuthenticationPrincipal UserContext user
    ) {
        validateReader(user);
        DevelopmentSpecService.ExportDocument exportDocument = developmentSpecService.export(id, format, user);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(exportDocument.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + exportDocument.filename() + "\"")
                .body(exportDocument.content());
    }

    @PostMapping
    public ResponseEntity<DevelopmentSpecDto> create(
            @Valid @RequestBody DevelopmentSpecDto.UpsertRequest body,
            @AuthenticationPrincipal UserContext user
    ) {
        validateEditor(user);
        return ResponseEntity.ok(DevelopmentSpecDto.from(developmentSpecService.create(body, user)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<DevelopmentSpecDto> update(
            @PathVariable String id,
            @Valid @RequestBody DevelopmentSpecDto.UpsertRequest body,
            @AuthenticationPrincipal UserContext user
    ) {
        validateEditor(user);
        return ResponseEntity.ok(DevelopmentSpecDto.from(developmentSpecService.update(id, body, user)));
    }

    @PostMapping("/{id}/generate")
    public ResponseEntity<DevelopmentSpecDto> generate(
            @PathVariable String id,
            @AuthenticationPrincipal UserContext user
    ) {
        validateEditor(user);
        return ResponseEntity.ok(DevelopmentSpecDto.from(developmentSpecService.generate(id, user)));
    }

    private void validateReader(UserContext user) {
        if (user == null) {
            throw new ForbiddenAppException("view_development_spec");
        }
    }

    private void validateEditor(UserContext user) {
        if (user == null || (!user.hasRole("DEVELOPER") && !user.hasRole("TL") && !user.hasRole("DEVOPS_ADMIN"))) {
            throw new ForbiddenAppException("edit_development_spec");
        }
    }
}

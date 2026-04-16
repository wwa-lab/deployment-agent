package com.wwa.agenthub.platform.web.shared;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.domain.fileimport.UploadTemplateService;
import com.wwa.agenthub.errors.ForbiddenAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;

/**
 * Template download controller (platform-level).
 *
 * <pre>
 *   GET /api/platform/upload/template — shared XLSX rundown template
 * </pre>
 *
 * <p>Extracted from the Deployment Agent UploadController in BA-T15 so that all agents
 * (Deployment, Testing, Build) download the same neutrally-named template without
 * going through an agent-prefixed route. Authorization: any role permitted to upload
 * (DEVELOPER, TL, DEVOPS_ADMIN).
 */
@RestController
@RequestMapping("/api/platform/upload")
@RequiredArgsConstructor
public class TemplateDownloadController {

    private final UploadTemplateService uploadTemplateService;

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(@AuthenticationPrincipal UserContext user) throws IOException {
        validateUploadRole(user);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"request-template.xlsx\"")
                .body(uploadTemplateService.generateTemplate());
    }

    private void validateUploadRole(UserContext user) {
        if (user == null || (!user.hasRole("DEVELOPER")
                && !user.hasRole("TL")
                && !user.hasRole("DEVOPS_ADMIN"))) {
            throw new ForbiddenAppException("upload_template");
        }
    }
}

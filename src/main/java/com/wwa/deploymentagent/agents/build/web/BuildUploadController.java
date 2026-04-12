package com.wwa.deploymentagent.agents.build.web;

import com.wwa.deploymentagent.contracts.AgentId;
import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.UploadResponseDto;
import com.wwa.deploymentagent.domain.fileimport.ImportResult;
import com.wwa.deploymentagent.domain.fileimport.ImportService;
import com.wwa.deploymentagent.domain.fileimport.TemplateSchema;
import com.wwa.deploymentagent.domain.fileimport.UploadTemplateService;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import com.wwa.deploymentagent.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Build Agent upload controller (BA-T21).
 *
 * <pre>
 *   POST /api/build-agent/upload   (multipart: file + optional releaseId + optional scope)
 * </pre>
 *
 * <p>PL-6 / BA-1 boundary: agent is forced to {@link AgentId#BUILD_AGENT} and stage is
 * forced to {@code "DEV"}, regardless of client-supplied params.
 */
@RestController
@RequestMapping("/api/build-agent/upload")
@RequiredArgsConstructor
public class BuildUploadController {

    private static final String FORCED_STAGE = "DEV";

    private final ImportService importService;
    private final UploadTemplateService uploadTemplateService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<UploadResponseDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "stage", required = false) String stageParam, // ignored
            @RequestParam(value = "releaseId", required = false) String releaseId,
            @RequestParam(value = "snowGroup", required = false) String snowGroup,
            @RequestParam(value = "application", required = false) String application,
            @RequestParam(value = "agent", required = false) String agent, // ignored
            @AuthenticationPrincipal UserContext user) throws IOException {
        validateUploadRole(user);

        if (file.isEmpty()) {
            throw new ValidationAppException("Uploaded file is empty");
        }

        ImportResult result = importService.importFile(
                file.getBytes(),
                FORCED_STAGE,
                user,
                releaseId,
                snowGroup,
                application,
                AgentId.BUILD_AGENT);
        return ResponseEntity.ok(UploadResponseDto.from(result));
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(
            @AuthenticationPrincipal UserContext user) throws IOException {
        validateUploadRole(user);

        TemplateSchema schema = uploadTemplateService.resolveSchema(AgentId.BUILD_AGENT);
        byte[] bytes = uploadTemplateService.generateTemplate(AgentId.BUILD_AGENT);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + schema.fileName() + "\"")
                .body(bytes);
    }

    private void validateUploadRole(UserContext user) {
        if (user == null || (!user.hasRole("DEVELOPER")
                && !user.hasRole("TL")
                && !user.hasRole("DEVOPS_ADMIN"))) {
            throw new ForbiddenAppException("upload");
        }
    }
}

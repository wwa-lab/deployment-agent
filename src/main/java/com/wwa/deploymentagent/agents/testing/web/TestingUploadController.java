package com.wwa.deploymentagent.agents.testing.web;

import com.wwa.deploymentagent.contracts.AgentId;
import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.UploadResponseDto;
import com.wwa.deploymentagent.domain.fileimport.ImportResult;
import com.wwa.deploymentagent.domain.fileimport.ImportService;
import com.wwa.deploymentagent.errors.ForbiddenAppException;
import com.wwa.deploymentagent.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

/**
 * Testing Agent Upload controller – accepts an XLSX file + stage, runs the full import.
 *
 * <pre>
 *   POST /api/testing-agent/upload   (multipart: file + stage + optional releaseId)
 * </pre>
 *
 * <p>Template download moved to {@code GET /api/platform/upload/template} in BA-T15.
 *
 * <p>Authorization: DEVELOPER, TL, or DEVOPS_ADMIN role.
 * <p>Agent is always forced to {@link AgentId#TESTING_AGENT} regardless of client-supplied param.
 */
@RestController
@RequestMapping("/api/testing-agent/upload")
@RequiredArgsConstructor
public class TestingUploadController {

    private final ImportService importService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<UploadResponseDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("stage") String stageParam,
            @RequestParam(value = "releaseId", required = false) String releaseId,
            @RequestParam(value = "snowGroup", required = false) String snowGroup,
            @RequestParam(value = "application", required = false) String application,
            @RequestParam(value = "agent", required = false) String agent, // accepted but ignored — overridden server-side
            @AuthenticationPrincipal UserContext user) throws IOException {
        validateUploadRole(user);

        // Validate stage before touching the file
        String stage = stageParam.trim().toUpperCase();
        if (!java.util.Set.of("SIT", "UAT", "PROD").contains(stage)) {
            throw new ValidationAppException(
                    "Invalid stage: '" + stageParam + "'. Must be SIT, UAT, or PROD.");
        }

        if (file.isEmpty()) {
            throw new ValidationAppException("Uploaded file is empty");
        }

        // Security boundary: always use TESTING_AGENT regardless of client-supplied agent param
        ImportResult result = importService.importFile(
                file.getBytes(),
                stage,
                user,
                releaseId,
                snowGroup,
                application,
                AgentId.TESTING_AGENT);
        return ResponseEntity.ok(UploadResponseDto.from(result));
    }

    private void validateUploadRole(UserContext user) {
        if (user == null || (!user.hasRole("DEVELOPER")
                && !user.hasRole("TL")
                && !user.hasRole("DEVOPS_ADMIN"))) {
            throw new ForbiddenAppException("upload");
        }
    }
}

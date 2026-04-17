package com.wwa.agenthub.agents.project.web;

import com.wwa.agenthub.agents.project.domain.ProjectStage;
import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.UploadResponseDto;
import com.wwa.agenthub.domain.fileimport.ImportResult;
import com.wwa.agenthub.domain.fileimport.ImportService;
import com.wwa.agenthub.domain.fileimport.TemplateSchema;
import com.wwa.agenthub.domain.fileimport.UploadTemplateService;
import com.wwa.agenthub.errors.ForbiddenAppException;
import com.wwa.agenthub.errors.ValidationAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

@RestController
@RequestMapping("/api/project-agent/upload")
@RequiredArgsConstructor
public class ProjectUploadController {

    private final ImportService importService;
    private final UploadTemplateService uploadTemplateService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<UploadResponseDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("stage") String stageParam,
            @RequestParam(value = "releaseId", required = false) String releaseId,
            @RequestParam(value = "snowGroup", required = false) String snowGroup,
            @RequestParam(value = "application", required = false) String application,
            @RequestParam(value = "agent", required = false) String agent,
            @AuthenticationPrincipal UserContext user) throws IOException {
        validateUploadRole(user);

        String stage;
        try {
            stage = ProjectStage.fromString(stageParam.trim().toUpperCase()).name();
        } catch (Exception ex) {
            throw new ValidationAppException(
                    "Invalid stage: '" + stageParam + "'. Must be one of REQUIREMENT, FUNCTIONAL_DESIGN, "
                            + "TECHNICAL_DESIGN, DEVELOPMENT, TESTING, PERFORMANCE_TEST, RESULT_SIGNOFF, "
                            + "BUSINESS_ENDORSEMENT, CAB, DEPLOYMENT, POST_IMPLEMENTATION.");
        }

        if (file.isEmpty()) {
            throw new ValidationAppException("Uploaded file is empty");
        }

        ImportResult result = importService.importFile(
                file.getBytes(),
                stage,
                user,
                releaseId,
                snowGroup,
                application,
                AgentId.PROJECT_AGENT);
        return ResponseEntity.ok(UploadResponseDto.from(result));
    }

    @GetMapping("/template")
    public ResponseEntity<byte[]> downloadTemplate(
            @AuthenticationPrincipal UserContext user) throws IOException {
        validateUploadRole(user);

        TemplateSchema schema = uploadTemplateService.resolveSchema(AgentId.PROJECT_AGENT);
        byte[] bytes = uploadTemplateService.generateTemplate(AgentId.PROJECT_AGENT);
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

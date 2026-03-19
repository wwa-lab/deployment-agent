package com.wwa.deploymentagent.web.controller;

import com.wwa.deploymentagent.contracts.UserContext;
import com.wwa.deploymentagent.contracts.dto.UploadResponseDto;
import com.wwa.deploymentagent.contracts.enums.Stage;
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
 * Upload controller – accepts an XLSX file + stage, runs the full import.
 *
 * <pre>
 *   POST /api/deployment-agent/upload   (multipart: file + stage)
 * </pre>
 *
 * <p>Authorization: DEVELOPER or TL role.
 */
@RestController
@RequestMapping("/api/deployment-agent/upload")
@RequiredArgsConstructor
public class UploadController {

    private final ImportService importService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<UploadResponseDto> upload(
            @RequestParam("file") MultipartFile file,
            @RequestParam("stage") String stageParam,
            @AuthenticationPrincipal UserContext user) throws IOException {
        if (!"DEVELOPER".equals(user.role()) && !"TL".equals(user.role())) {
            throw new ForbiddenAppException("upload");
        }

        // Validate stage before touching the file
        Stage stage;
        try {
            stage = Stage.valueOf(stageParam.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ValidationAppException(
                    "Invalid stage: '" + stageParam + "'. Must be SIT, UAT, or PROD.");
        }

        if (file.isEmpty()) {
            throw new ValidationAppException("Uploaded file is empty");
        }

        ImportResult result = importService.importFile(file.getBytes(), stage, user);
        return ResponseEntity.ok(UploadResponseDto.from(result));
    }
}

package com.wwa.agenthub.agents.testing.web;

import com.wwa.agenthub.contracts.UserContext;
import com.wwa.agenthub.contracts.dto.CsvCompareResponseDto;
import com.wwa.agenthub.domain.filecompare.CsvCompareService;
import com.wwa.agenthub.errors.ForbiddenAppException;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/testing-agent/file-compare")
@RequiredArgsConstructor
public class TestingFileCompareController {

    private final CsvCompareService csvCompareService;

    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<CsvCompareResponseDto> compare(
            @RequestParam("files") List<MultipartFile> files,
            @AuthenticationPrincipal UserContext user) throws IOException {
        validateCompareRole(user);
        return ResponseEntity.ok(csvCompareService.compare(files));
    }

    private void validateCompareRole(UserContext user) {
        if (user == null || (!user.hasRole("DEVELOPER")
                && !user.hasRole("TL")
                && !user.hasRole("DEVOPS_ADMIN"))) {
            throw new ForbiddenAppException("file_compare");
        }
    }
}

package com.wwa.agenthub.web;

import com.wwa.agenthub.domain.fileimport.UploadTemplateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("UploadController")
class UploadControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UploadTemplateService uploadTemplateService;

    @Test
    @DisplayName("GET /upload/template with developer auth returns downloadable xlsx")
    void downloadTemplate_returnsWorkbook() throws Exception {
        mockMvc.perform(get("/api/platform/upload/template")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"request-template.xlsx\""))
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @DisplayName("GET /upload/template with DevOps Admin auth returns downloadable xlsx")
    void downloadTemplate_devOpsAdminCanDownloadTemplate() throws Exception {
        mockMvc.perform(get("/api/platform/upload/template")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"request-template.xlsx\""))
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @DisplayName("POST /upload persists and returns uploaded scope context plus explicit release identifier without requiring a client agent param")
    void upload_returnsScopeContext() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "deployment.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                uploadTemplateService.generateTemplate());

        mockMvc.perform(multipart("/api/deployment-agent/upload")
                        .file(file)
                        .param("stage", "SIT")
                        .param("releaseId", "WFPROJ-20260327-01")
                        .param("application", "AMH HCC")
                        .param("snowGroup", "HTSA-CSI-HCC-AMH-PRJ")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.releaseId").value("WFPROJ-20260327-01"))
                .andExpect(jsonPath("$.application").value("AMH HCC"))
                .andExpect(jsonPath("$.snowGroup").value("HTSA-CSI-HCC-AMH-PRJ"))
                // Post-BA-T19 Deployment Upload forces agent = "deployment-agent"
                // server-side (PL-6), overriding any client-supplied value.
                .andExpect(jsonPath("$.agent").value("deployment-agent"));
    }

    @Test
    @DisplayName("POST /upload returns a friendly message for invalid Excel format")
    void upload_invalidExcelFormatReturnsFriendlyMessage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "broken.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "not-a-real-xlsx".getBytes());

        mockMvc.perform(multipart("/api/deployment-agent/upload")
                        .file(file)
                        .param("stage", "SIT")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.message").value(
                        "Invalid Excel file format. Please upload a valid .xlsx file, preferably using the downloaded template."));
    }
}

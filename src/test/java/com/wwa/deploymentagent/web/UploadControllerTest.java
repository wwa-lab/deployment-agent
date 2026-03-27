package com.wwa.deploymentagent.web;

import com.wwa.deploymentagent.domain.fileimport.UploadTemplateService;
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
        mockMvc.perform(get("/api/deployment-agent/upload/template")
                        .header("X-User-Id", "emp-001")
                        .header("X-User-Role", "DEVELOPER"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"deployment-request-template.xlsx\""))
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @DisplayName("GET /upload/template with DevOps Admin auth returns downloadable xlsx")
    void downloadTemplate_devOpsAdminCanDownloadTemplate() throws Exception {
        mockMvc.perform(get("/api/deployment-agent/upload/template")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"deployment-request-template.xlsx\""))
                .andExpect(header().string("Content-Type",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    @DisplayName("POST /upload persists and returns uploaded scope context")
    void upload_returnsScopeContext() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "deployment.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                uploadTemplateService.generateTemplate());

        mockMvc.perform(multipart("/api/deployment-agent/upload")
                        .file(file)
                        .param("stage", "SIT")
                        .param("application", "AMH HCC")
                        .param("snowGroup", "HTSA-CSI-HCC-AMH-PRJ")
                        .param("agent", "Deployment Agent")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("AMH HCC"))
                .andExpect(jsonPath("$.snowGroup").value("HTSA-CSI-HCC-AMH-PRJ"))
                .andExpect(jsonPath("$.agent").value("Deployment Agent"));
    }
}

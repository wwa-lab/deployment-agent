package com.wwa.deploymentagent.web;

import com.wwa.deploymentagent.contracts.AgentId;
import com.wwa.deploymentagent.domain.fileimport.UploadTemplateService;
import com.wwa.deploymentagent.domain.releaseflow.RequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("TestingAgentUploadController")
class TestingAgentUploadControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UploadTemplateService uploadTemplateService;
    @Autowired private RequestRepository requestRepository;

    @Test
    @DisplayName("GET /api/platform/upload/template returns neutral xlsx filename")
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
    @DisplayName("POST /upload tags request with testing-agent agent value")
    void upload_tagsRequestWithTestingAgent() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "testing.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                uploadTemplateService.generateTemplate());

        mockMvc.perform(multipart("/api/testing-agent/upload")
                        .file(file)
                        .param("stage", "SIT")
                        .param("releaseId", "TA-20260331-01")
                        .param("application", "AMH HCC")
                        .param("snowGroup", "HTSA-CSI-HCC-AMH-PRJ")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent").value(AgentId.TESTING_AGENT));

        // Verify the Request entity in DB has agent = "testing-agent"
        var requests = requestRepository.findAll();
        assertThat(requests).isNotEmpty();
        assertThat(requests).allMatch(r -> AgentId.TESTING_AGENT.equals(r.getAgent()));
    }

    @Test
    @DisplayName("POST /upload overrides client-supplied agent param")
    void upload_overridesClientAgentParam() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "testing.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                uploadTemplateService.generateTemplate());

        mockMvc.perform(multipart("/api/testing-agent/upload")
                        .file(file)
                        .param("stage", "SIT")
                        .param("agent", "deployment-agent") // should be overridden
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.agent").value(AgentId.TESTING_AGENT));
    }

    @Test
    @DisplayName("POST /upload forbidden without required role")
    void upload_forbiddenWithoutRole() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "testing.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                uploadTemplateService.generateTemplate());

        mockMvc.perform(multipart("/api/testing-agent/upload")
                        .file(file)
                        .param("stage", "SIT")
                        .header("X-User-Id", "emp-999")
                        .header("X-User-Role", "AUDIT"))
                .andExpect(status().isForbidden());
    }
}

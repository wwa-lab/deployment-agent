package com.wwa.agenthub.web;

import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.domain.fileimport.UploadTemplateService;
import com.wwa.agenthub.domain.releaseflow.RequestRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("ProjectAgentUploadController")
class ProjectAgentUploadControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UploadTemplateService uploadTemplateService;
    @Autowired private RequestRepository requestRepository;

    @Test
    @DisplayName("POST /api/project-agent/upload tags request with project-agent and selected lifecycle stage")
    void upload_infersProjectAgentAndSelectedStage() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "project.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                uploadTemplateService.generateTemplate(AgentId.PROJECT_AGENT));

        mockMvc.perform(multipart("/api/project-agent/upload")
                        .file(file)
                        .param("stage", "REQUIREMENT")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("REQUIREMENT"))
                .andExpect(jsonPath("$.agent").value(AgentId.PROJECT_AGENT));

        var requests = requestRepository.findAll();
        assertThat(requests).isNotEmpty();
        assertThat(requests).allMatch(request -> AgentId.PROJECT_AGENT.equals(request.getAgent()));
        assertThat(requests).allMatch(request -> "REQUIREMENT".equals(request.getStage()));
    }
}

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("BuildAgentUploadController")
class BuildAgentUploadControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UploadTemplateService uploadTemplateService;
    @Autowired private RequestRepository requestRepository;

    @Test
    @DisplayName("POST /api/build-agent/upload infers build-agent without requiring client agent metadata")
    void upload_infersBuildAgentWithoutClientParam() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "build.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                uploadTemplateService.generateTemplate());

        mockMvc.perform(multipart("/api/build-agent/upload")
                        .file(file)
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stage").value("DEV"))
                .andExpect(jsonPath("$.agent").value(AgentId.BUILD_AGENT));

        var requests = requestRepository.findAll();
        assertThat(requests).isNotEmpty();
        assertThat(requests).allMatch(request -> AgentId.BUILD_AGENT.equals(request.getAgent()));
        assertThat(requests).allMatch(request -> "DEV".equals(request.getStage()));
    }
}

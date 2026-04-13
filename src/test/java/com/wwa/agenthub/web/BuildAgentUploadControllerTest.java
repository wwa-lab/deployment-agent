package com.wwa.agenthub.web;

import com.wwa.agenthub.contracts.AgentId;
import com.wwa.agenthub.domain.fileimport.UploadTemplateService;
import com.wwa.agenthub.domain.releaseflow.RequestRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
    @DisplayName("GET /api/build-agent/upload/template returns the common IBM i skill starter pack")
    void downloadTemplate_returnsSkillStarterPack() throws Exception {
        byte[] bytes = mockMvc.perform(get("/api/build-agent/upload/template")
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition",
                        "attachment; filename=\"request-template.xlsx\""))
                .andReturn()
                .getResponse()
                .getContentAsByteArray();

        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            var sheet = workbook.getSheet("AMH_HCC_task");
            assertThat(sheet).isNotNull();
            assertThat(sheet.getLastRowNum()).isEqualTo(16);

            List<String> scripts = new ArrayList<>();
            for (int rowIndex = 1; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
                var row = sheet.getRow(rowIndex);
                assertThat(row).isNotNull();
                assertThat(row.getCell(6).getStringCellValue()).isEqualTo("MANUAL");
                scripts.add(row.getCell(7).getStringCellValue());
                assertThat(row.getCell(8).getStringCellValue()).contains("\"runner\":\"cli-skill\"");
            }

            assertThat(scripts.get(0)).isEqualTo("ibm-i-workflow-orchestrator");
            assertThat(scripts).contains(
                    "ibm-i-requirement-normalizer",
                    "ibm-i-technical-design",
                    "ibm-i-code-generator",
                    "ibm-i-workflow-orchestrator"
            );
        }
    }

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

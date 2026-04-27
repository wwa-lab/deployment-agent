package com.wwa.agenthub.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import org.springframework.beans.factory.annotation.Autowired;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("TestingFileCompareController")
class TestingFileCompareControllerTest {

    @Autowired private MockMvc mockMvc;

    @Test
    @DisplayName("POST /api/testing-agent/file-compare returns row and cell differences")
    void compare_returnsDifferences() throws Exception {
        MockMultipartFile base = csv("files", "base.csv",
                "id,name,status\n1,Alice,pass\n2,Bob,pass\n3,Chen,hold\n");
        MockMultipartFile target = csv("files", "target.csv",
                "id,name,status\n1,Alice,pass\n2,Bob,fail\n4,Dina,pass\n");

        mockMvc.perform(multipart("/api/testing-agent/file-compare")
                        .file(base)
                        .file(target)
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.baseFileName").value("base.csv"))
                .andExpect(jsonPath("$.fileCount").value(2))
                .andExpect(jsonPath("$.comparisons[0].fileName").value("target.csv"))
                .andExpect(jsonPath("$.comparisons[0].matchedRows").value(1))
                .andExpect(jsonPath("$.comparisons[0].changedRows").value(2))
                .andExpect(jsonPath("$.comparisons[0].totalDifferences").value(4))
                .andExpect(jsonPath("$.comparisons[0].differences[0].rowNumber").value(3))
                .andExpect(jsonPath("$.comparisons[0].differences[0].column").value("status"))
                .andExpect(jsonPath("$.comparisons[0].differences[0].baseValue").value("pass"))
                .andExpect(jsonPath("$.comparisons[0].differences[0].compareValue").value("fail"));
    }

    @Test
    @DisplayName("POST /api/testing-agent/file-compare rejects mismatched headers")
    void compare_rejectsMismatchedHeaders() throws Exception {
        MockMultipartFile base = csv("files", "base.csv", "id,name\n1,Alice\n");
        MockMultipartFile target = csv("files", "target.csv", "id,status\n1,pass\n");

        mockMvc.perform(multipart("/api/testing-agent/file-compare")
                        .file(base)
                        .file(target)
                        .header("X-User-Id", "emp-003")
                        .header("X-User-Role", "DEVOPS_ADMIN"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("CSV headers must match. File 'target.csv' has different headers from base file 'base.csv'."));
    }

    @Test
    @DisplayName("POST /api/testing-agent/file-compare requires upload role")
    void compare_forbiddenWithoutUploadRole() throws Exception {
        mockMvc.perform(multipart("/api/testing-agent/file-compare")
                        .file(csv("files", "base.csv", "id\n1\n"))
                        .file(csv("files", "target.csv", "id\n1\n"))
                        .header("X-User-Id", "emp-999")
                        .header("X-User-Role", "AUDIT"))
                .andExpect(status().isForbidden());
    }

    private MockMultipartFile csv(String paramName, String fileName, String body) {
        return new MockMultipartFile(paramName, fileName, "text/csv", body.getBytes());
    }
}

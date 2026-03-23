package com.wwa.deploymentagent.web;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("UploadController")
class UploadControllerTest {

    @Autowired private MockMvc mockMvc;

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
}

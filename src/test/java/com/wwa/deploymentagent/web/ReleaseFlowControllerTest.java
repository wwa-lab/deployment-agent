package com.wwa.deploymentagent.web;

import com.wwa.deploymentagent.domain.releaseflow.ReleaseFlow;
import com.wwa.deploymentagent.domain.releaseflow.Request;
import com.wwa.deploymentagent.domain.task.Task;
import com.wwa.deploymentagent.helper.TestDataHelper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@DisplayName("T13.3 - ReleaseFlowController API contract")
class ReleaseFlowControllerTest {

    private static final String BASE = "/api/deployment-agent/release-flows";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private TestDataHelper helper;

    // ─── list ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("list_returnsEmptyPage_whenNoData - GET /release-flows returns 200 with empty content array")
    void list_returnsEmptyPage_whenNoData() throws Exception {
        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    @DisplayName("list_returnsFlows_withSeedData - seeded release flow appears in content")
    void list_returnsFlows_withSeedData() throws Exception {
        helper.seedReleaseFlow();

        mockMvc.perform(get(BASE)
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.total").value(1));
    }

    // ─── getById ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getById_returnsDetail - GET /release-flows/{id} returns 200 with id and projectId")
    void getById_returnsDetail() throws Exception {
        ReleaseFlow rf = helper.seedReleaseFlow();
        Request req = helper.seedRequest(rf);
        Task task = helper.seedTask(req);

        mockMvc.perform(get(BASE + "/" + rf.getId())
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(rf.getId()))
                .andExpect(jsonPath("$.projectId").value(rf.getProjectId()));
    }

    @Test
    @DisplayName("getById_unknownId_returns404 - GET /release-flows/unknown-xyz returns 404")
    void getById_unknownId_returns404() throws Exception {
        mockMvc.perform(get(BASE + "/unknown-xyz")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isNotFound());
    }

    // ─── pagination validation ────────────────────────────────────────────────

    @Test
    @DisplayName("list_invalidPageParam_returns400 - page=-1 returns 400")
    void list_invalidPageParam_returns400() throws Exception {
        mockMvc.perform(get(BASE)
                        .param("page", "-1")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("list_invalidSizeParam_returns400 - size=0 returns 400")
    void list_invalidSizeParam_returns400() throws Exception {
        mockMvc.perform(get(BASE)
                        .param("size", "0")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("list_sizeTooLarge_returns400 - size=101 returns 400")
    void list_sizeTooLarge_returns400() throws Exception {
        mockMvc.perform(get(BASE)
                        .param("size", "101")
                        .header("X-User-Id", "user1")
                        .header("X-User-Role", "TL"))
                .andExpect(status().isBadRequest());
    }
}

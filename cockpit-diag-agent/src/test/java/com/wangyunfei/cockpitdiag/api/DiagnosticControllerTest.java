package com.wangyunfei.cockpitdiag.api;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.annotation.DirtiesContext;

@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class DiagnosticControllerTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void diagnosesOverTemperatureAndGeneratesTestPlan() throws Exception {
        mockMvc.perform(post("/api/v1/signals")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"speedKph": 80, "coolantTemperatureC": 120, "gear": "D"}
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"question": "为什么出现超温告警，请生成测试方案", "includeTestPlan": true}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.risk").value("CRITICAL"))
                .andExpect(jsonPath("$.confidence").value(0.72))
                .andExpect(jsonPath("$.controlActionApprovalRequired").value(true))
                .andExpect(jsonPath("$.evidence[0].code").value("OVER_TEMPERATURE"))
                .andExpect(jsonPath("$.testPlan[0].name").value("超温阈值与恢复测试"));
    }

    @Test
    void importsCppMonitorLog() throws Exception {
        String log = """
                [2026-09-11 16:00:00.000][INFO] speed=30.00km/h coolant=90C gear=D
                [2026-09-11 16:00:00.010][WARN] alert=ILLEGAL_VALUE state=ACTIVE severity=WARNING message="gear=9"
                """;

        mockMvc.perform(post("/api/v1/monitor-log")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content(log))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.signalUpdated").value(true))
                .andExpect(jsonPath("$.alertsImported").value(1));
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsMissingIncludeTestPlanAsFalse() throws Exception {
        mockMvc.perform(post("/api/v1/diagnoses")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\": \"分析当前状态\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.testPlan").isEmpty());
    }
}

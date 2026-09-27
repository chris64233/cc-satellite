package com.chris64233.cc.satellite;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfig.class)
class ContactApiTest {

    @Autowired
    MockMvc mockMvc;
    @Autowired
    ObjectMapper objectMapper;

    @Test
    void endToEndContactLifecycle() throws Exception {
        // 创建地面站
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"API-GS-1","bands":["S"],
                                 "antennas":[{"antennaNumber":9001,"slewSeconds":300}]}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("API-GS-1"))
                .andExpect(jsonPath("$.antennas[0].antennaNumber").value(9001));

        // 创建过境窗口
        MvcResult windowResult = mockMvc.perform(post("/api/windows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"stationCode":"API-GS-1","satellite":"SAT-A",
                                 "startTime":"2026-09-27T10:00:00Z","endTime":"2026-09-27T11:00:00Z",
                                 "bands":["S"]}
                                """))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode window = objectMapper.readTree(windowResult.getResponse().getContentAsString());
        long windowId = window.get("id").asLong();

        String contactJson = """
                {"idempotencyKey":"API-K-1","windowId":%d,"band":"S",
                 "durationMinutes":20,"desiredStart":"2026-09-27T10:05:00Z"}
                """.formatted(windowId);

        // 排程：201
        MvcResult created = mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON).content(contactJson))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("2026-09-27T10:05:00Z"))
                .andExpect(jsonPath("$.endTime").value("2026-09-27T10:25:00Z"))
                .andExpect(jsonPath("$.antennaNumber").value(9001))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn();
        long contactId = objectMapper.readTree(created.getResponse().getContentAsString())
                .get("id").asLong();

        // 幂等重放：200 且返回原排程
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON).content(contactJson))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(contactId));

        // 相同幂等键、不同内容：409
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"API-K-1","windowId":%d,"band":"S",
                                 "durationMinutes":30,"desiredStart":"2026-09-27T10:05:00Z"}
                                """.formatted(windowId)))
                .andExpect(status().isConflict());

        // 频段不兼容：422，且不留下占用
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"API-K-2","windowId":%d,"band":"X",
                                 "durationMinutes":20,"desiredStart":"2026-09-27T10:30:00Z"}
                                """.formatted(windowId)))
                .andExpect(status().isUnprocessableEntity());

        // 联系详情
        mockMvc.perform(get("/api/contacts/{id}", contactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.satellite").value("SAT-A"))
                .andExpect(jsonPath("$.band").value("S"));

        // 地面站日程
        mockMvc.perform(get("/api/stations/{code}/schedule", "API-GS-1")
                        .param("from", "2026-09-27T09:00:00Z")
                        .param("to", "2026-09-27T12:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(contactId));

        // 取消：释放天线，重复取消幂等
        mockMvc.perform(delete("/api/contacts/{id}", contactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        mockMvc.perform(delete("/api/contacts/{id}", contactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 取消后时段释放，可重新排程
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"API-K-3","windowId":%d,"band":"S",
                                 "durationMinutes":20,"desiredStart":"2026-09-27T10:05:00Z"}
                                """.formatted(windowId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("2026-09-27T10:05:00Z"));
    }

    @Test
    void unknownResourcesReturn404() throws Exception {
        mockMvc.perform(get("/api/contacts/{id}", 424242))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/stations/{code}/schedule", "NO-SUCH-STATION"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"idempotencyKey":"API-K-404","windowId":424242,"band":"S",
                                 "durationMinutes":20,"desiredStart":"2026-09-27T10:05:00Z"}
                                """))
                .andExpect(status().isNotFound());
    }
}

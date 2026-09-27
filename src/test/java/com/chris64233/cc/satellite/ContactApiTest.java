package com.chris64233.cc.satellite;

import com.chris64233.cc.satellite.repository.ContactRepository;
import com.chris64233.cc.satellite.repository.GroundStationRepository;
import com.chris64233.cc.satellite.repository.VisibilityWindowRepository;
import com.chris64233.cc.satellite.support.TestClockConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestClockConfiguration.class)
class ContactApiTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ContactRepository contactRepository;
    @Autowired
    private GroundStationRepository stationRepository;
    @Autowired
    private VisibilityWindowRepository windowRepository;

    @BeforeEach
    void cleanUp() {
        contactRepository.deleteAll();
        windowRepository.deleteAll();
        stationRepository.deleteAll();
    }

    private long createStationAndWindow() throws Exception {
        mockMvc.perform(post("/api/stations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "code": "ST-API",
                                  "supportedBands": ["X"],
                                  "antennas": [{"code": "ANT-1", "slewSeconds": 600}]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("ST-API"))
                .andExpect(jsonPath("$.antennas[0].slewSeconds").value(600));

        String windowResponse = mockMvc.perform(post("/api/windows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "stationCode": "ST-API",
                                  "satellite": "SAT-1",
                                  "startTime": "2026-06-01T10:00:00Z",
                                  "endTime": "2026-06-01T11:00:00Z",
                                  "bands": ["X"]
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.stationCode").value("ST-API"))
                .andReturn().getResponse().getContentAsString();
        return Long.parseLong(windowResponse.replaceAll(".*\"id\":(\\d+).*", "$1"));
    }

    private String contactBody(String key, long windowId, long minutes) {
        return """
                {
                  "idempotencyKey": "%s",
                  "windowId": %d,
                  "band": "X",
                  "durationMinutes": %d,
                  "desiredStart": "2026-06-01T10:00:00Z"
                }
                """.formatted(key, windowId, minutes);
    }

    @Test
    void fullContactLifecycle() throws Exception {
        long windowId = createStationAndWindow();

        String scheduled = mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("api-k1", windowId, 60)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startTime").value("2026-06-01T10:00:00Z"))
                .andExpect(jsonPath("$.endTime").value("2026-06-01T11:00:00Z"))
                .andExpect(jsonPath("$.status").value("SCHEDULED"))
                .andReturn().getResponse().getContentAsString();
        long contactId = Long.parseLong(scheduled.replaceAll(".*\"id\":(\\d+).*", "$1"));

        // 幂等重放：同键同内容返回原排程
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("api-k1", windowId, 60)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(contactId));

        // 同键不同内容 -> 409
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("api-k1", windowId, 30)))
                .andExpect(status().isConflict());

        // 窗口已被占满 -> 422
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("api-k2", windowId, 30)))
                .andExpect(status().isUnprocessableContent());

        // 详情查询
        mockMvc.perform(get("/api/contacts/{id}", contactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idempotencyKey").value("api-k1"));

        // 取消（测试时钟固定在窗口开始之前）
        mockMvc.perform(post("/api/contacts/{id}/cancel", contactId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        // 站点日程：取消后不再占用
        mockMvc.perform(get("/api/stations/{code}/schedule", "ST-API"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.antennas[0].contacts").isEmpty());

        // 取消后时段释放，可以重新排程
        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(contactBody("api-k3", windowId, 60)))
                .andExpect(status().isCreated());
    }

    @Test
    void unknownResourcesReturn404AndInvalidBodyReturns400() throws Exception {
        mockMvc.perform(get("/api/contacts/{id}", 123456L))
                .andExpect(status().isNotFound());
        mockMvc.perform(get("/api/stations/{code}/schedule", "NOPE"))
                .andExpect(status().isNotFound());
        mockMvc.perform(post("/api/contacts/{id}/cancel", 123456L))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/contacts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"windowId": 1, "band": "X", "durationMinutes": 0}
                                """))
                .andExpect(status().isBadRequest());
    }
}

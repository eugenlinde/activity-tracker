package dev.eugenlinde.activitytracker.config;

import dev.eugenlinde.activitytracker.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@ActiveProfiles("test")
class HealthEndpointTest {
    @Autowired
    private MockMvc mockMvc;

    @Test
    void health_withoutToken_returns200AndStatusUpWithoutDetails() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
    }

    @ParameterizedTest
    @ValueSource(strings = {"/actuator", "/actuator/env", "/actuator/beans", "/actuator/info", "/actuator/metrics"})
    void otherActuatorEndpoints_areNotReachableWithoutAToken(String path) throws Exception {
        int statusCode = mockMvc.perform(get(path)).andReturn().getResponse().getStatus();

        assertNotEquals(200, statusCode);
    }

    @Test
    void apiStillRequiresAToken() throws Exception {
        mockMvc.perform(get("/api/activities")).andExpect(status().isUnauthorized());
    }
}

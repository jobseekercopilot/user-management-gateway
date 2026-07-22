package com.jobseekercopilot.usermanagementgateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties =
        "authentication.service.token=test-only-authentication-service-token-32-bytes")
@AutoConfigureMockMvc
class WebEndpointSmokeTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void actuatorHealthIsMapped() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist());
    }

    @Test
    void actuatorReadinessIncludesAvailableDownstreams() throws Exception {
        mockMvc.perform(get("/actuator/health/readiness"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.downstreamDependencies.status").value("UP"))
                .andExpect(jsonPath("$.components.downstreamDependencies.details.authentication-service")
                        .value("AVAILABLE"))
                .andExpect(jsonPath("$.components.downstreamDependencies.details.user-profile-service")
                        .value("AVAILABLE"));
    }

    @Test
    void actuatorMetricsAreNotPubliclyExposed() throws Exception {
        mockMvc.perform(get("/actuator/metrics"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void openApiDocsAreMapped() throws Exception {
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/auth/register'].post").exists());
    }
}

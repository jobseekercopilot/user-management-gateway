package com.jobseekercopilot.usermanagementgateway;

import com.jobseekercopilot.generated.authenticationservice.api.AuthenticationApi;
import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "authentication.service.token=test-only-authentication-service-token-32-bytes",
        "gateway.security.auth-rate-maximum=1",
        "gateway.security.auth-rate-global-maximum=10"
})
@AutoConfigureMockMvc
class GatewayRateLimitIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationApi authenticationApi;

    @MockBean
    private UserProfilesApi userProfilesApi;

    @Test
    void repeatedAuthenticationRequestsAreBoundedBeforeCredentialsReachDownstream() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/auth/login")
                        .header("X-Forwarded-For", "198.51.100.200")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_AUTHENTICATION_ATTEMPTS"))
                .andExpect(content().string(not(containsString("198.51.100.200"))));

        verifyNoInteractions(authenticationApi, userProfilesApi);
    }
}

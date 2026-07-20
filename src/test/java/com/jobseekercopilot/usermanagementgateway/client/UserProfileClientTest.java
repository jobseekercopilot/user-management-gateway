package com.jobseekercopilot.usermanagementgateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserProfileClientTest {

    @Test
    void generatedUserProfileApiUsesConfiguredBasePath() {
        ClientHttpRequestInterceptor correlation = (request, body, execution) ->
                execution.execute(request, body);
        var resilience = new DownstreamResilienceInterceptor("user-profile-service", 2, 3, 30_000, 32);
        var api = new DownstreamApiConfig().userProfilesApi(
                "http://localhost:8085", correlation, resilience, 500, 2_000);

        assertEquals("http://localhost:8085", api.getApiClient().getBasePath());
    }
}

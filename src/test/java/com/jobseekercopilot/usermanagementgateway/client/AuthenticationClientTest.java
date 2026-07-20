package com.jobseekercopilot.usermanagementgateway.config;

import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthenticationClientTest {

    @Test
    void generatedAuthenticationApiUsesConfiguredBasePath() {
        ClientHttpRequestInterceptor correlation = (request, body, execution) ->
                execution.execute(request, body);
        var resilience = new DownstreamResilienceInterceptor("authentication-service", 2, 3, 30_000, 32);
        var api = new DownstreamApiConfig().authenticationApi(
                "http://localhost:8084", correlation, resilience, 500, 2_000);

        assertEquals("http://localhost:8084", api.getApiClient().getBasePath());
    }
}

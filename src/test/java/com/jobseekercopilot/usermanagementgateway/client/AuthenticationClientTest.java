package com.jobseekercopilot.usermanagementgateway.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AuthenticationClientTest {

    @Test
    void generatedAuthenticationApiUsesConfiguredBasePath() {
        var api = new DownstreamApiConfig().authenticationApi("http://localhost:8084");

        assertEquals("http://localhost:8084", api.getApiClient().getBasePath());
    }
}

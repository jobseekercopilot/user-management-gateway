package com.jobseekercopilot.usermanagementgateway.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserProfileClientTest {

    @Test
    void generatedUserProfileApiUsesConfiguredBasePath() {
        var api = new DownstreamApiConfig().userProfilesApi("http://localhost:8085");

        assertEquals("http://localhost:8085", api.getApiClient().getBasePath());
    }
}

package com.jobseekercopilot.usermanagementgateway.config;

import com.jobseekercopilot.generated.authenticationservice.api.AuthenticationApi;
import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class DownstreamApiConfig {

    @Bean
    AuthenticationApi authenticationApi(
            @Value("${authentication.service.url}") String basePath) {
        var apiClient = new com.jobseekercopilot.generated.authenticationservice.client.ApiClient();
        apiClient.setBasePath(basePath);
        return new AuthenticationApi(apiClient);
    }

    @Bean
    UserProfilesApi userProfilesApi(
            @Value("${user.profile.service.url}") String basePath) {
        var apiClient = new com.jobseekercopilot.generated.userprofileservice.client.ApiClient();
        apiClient.setBasePath(basePath);
        return new UserProfilesApi(apiClient);
    }
}

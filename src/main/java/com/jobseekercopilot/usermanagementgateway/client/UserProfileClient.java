package com.jobseekercopilot.usermanagementgateway.client;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.jobseekercopilot.usermanagementgateway.model.UserProfile;

import jakarta.annotation.PostConstruct;

@Component
public class UserProfileClient {

    private RestClient restClient;

    @Value("${user.profile.service.url}")
    private String userProfileServiceUrl;

    @PostConstruct
    public void init() {
        this.restClient = RestClient.builder()
                .baseUrl(userProfileServiceUrl)
                .build();
    }

    public UserProfile getProfile(String userId) {
        return restClient.get()
                .uri("/api/profiles/me")
                .header("X-User-Id", userId)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .body(UserProfile.class);
    }

    public UserProfile createOrUpdateProfile(String userId, UserProfile profile) {
        return restClient.put()
                .uri("/api/profiles/me")
                .header("X-User-Id", userId)
                .contentType(MediaType.APPLICATION_JSON)
                .body(profile)
                .retrieve()
                .body(UserProfile.class);
    }
}

package com.jobseekercopilot.usermanagementgateway.client;

import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.jobseekercopilot.usermanagementgateway.model.LoginResponse;
import com.jobseekercopilot.usermanagementgateway.model.UserAccountResponse;

import jakarta.annotation.PostConstruct;

@Component
public class AuthenticationClient {

    private RestClient restClient;

    @Value("${authentication.service.url}")
    private String authServiceUrl;

    @PostConstruct
    public void init() {
        this.restClient = RestClient.builder()
                .baseUrl(authServiceUrl)
                .build();
    }

    public void register(String name, String email, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("name", name);
        body.put("email", email);
        body.put("password", password);

        restClient.post()
            .uri("/api/auth/register")
            .body(body)
            .retrieve()
            .toBodilessEntity();
    }

    public LoginResponse login(String email, String password) {
        Map<String, String> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);

        return restClient.post()
                .uri("/api/auth/login")
                .body(body)
                .retrieve()
                .body(LoginResponse.class);
    }

    public UserAccountResponse getUser(String token) {
    if (token == null || token.trim().isEmpty()) {
        throw new IllegalArgumentException("Token cannot be null or empty");
    }

    String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;

    return restClient.get()
            .uri("/api/auth/me")
            .header("Authorization", "Bearer " + cleanToken)
            .retrieve()
            .body(UserAccountResponse.class);
    }
}

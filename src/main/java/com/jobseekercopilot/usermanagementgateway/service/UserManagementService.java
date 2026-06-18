package com.jobseekercopilot.usermanagementgateway.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.usermanagementgateway.client.AuthenticationClient;
import com.jobseekercopilot.usermanagementgateway.client.UserProfileClient;
import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.model.LoginRequest;
import com.jobseekercopilot.usermanagementgateway.model.LoginResponse;
import com.jobseekercopilot.usermanagementgateway.model.RegisterRequest;
import com.jobseekercopilot.usermanagementgateway.model.User;
import com.jobseekercopilot.usermanagementgateway.model.UserAccountResponse;
import com.jobseekercopilot.usermanagementgateway.model.UserProfile;

@Service
@Primary
public class UserManagementService implements IUserManagementService {

    @Autowired
    private AuthenticationClient authenticationClient;

    @Autowired
    private UserProfileClient userProfileClient;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public GatewayResponse register(RegisterRequest request) {
        if (request == null) {
            return new GatewayResponse(400, false, "Invalid registration details.");
        }

        String name = request.getName();
        String email = request.getEmail();
        String password = request.getPassword();

        if (name == null || name.trim().length() < 2) {
            return new GatewayResponse(400, false, "Invalid registration details. Full name must be at least 2 characters.");
        }

        if (email == null || !email.contains("@")) {
            return new GatewayResponse(400, false, "Invalid registration details. Email address pattern is invalid.");
        }

        if (password == null || password.length() < 4) {
            return new GatewayResponse(400, false, "Invalid password. Must be at least 4 characters long.");
        }

        try {
            authenticationClient.register(name, email, password);
            
            LoginResponse loginResponse = authenticationClient.login(email, password);
            UserAccountResponse userAccountResponse = authenticationClient.getUser(loginResponse.getToken());
            
            UserProfile initialProfile = request.getProfile() != null ? request.getProfile() : new UserProfile("", "", "", "");
            UserProfile userProfile = userProfileClient.createOrUpdateProfile(userAccountResponse.getId(), initialProfile);

            User user = new User(userAccountResponse.getId(), name, email, userProfile, loginResponse.getToken());

            return new GatewayResponse(201, true, "Claimant account registered securely with the User Management Gateway.", user);
        } catch (HttpClientErrorException ex) {
            String errorMsg = getErrorMessage(ex);
            return new GatewayResponse(ex.getStatusCode().value(), false, errorMsg);
        } catch (Exception ex) {
            return new GatewayResponse(500, false, "Failed to register user: " + ex.getMessage());
        }
    }

    @Override
    public GatewayResponse login(LoginRequest request) {
        if (request == null) {
            return new GatewayResponse(400, false, "Missing credentials.");
        }

        String email = request.getEmail();
        String password = request.getPassword();

        if (email == null || password == null || email.trim().isEmpty() || password.isEmpty()) {
            return new GatewayResponse(400, false, "Missing credentials. Both email and password must be supplied.");
        }

        try {
            LoginResponse loginResponse = authenticationClient.login(email, password);
            UserAccountResponse userAccountResponse = authenticationClient.getUser(loginResponse.getToken());
            String userId = userAccountResponse.getId();

            // Resilient fallback using the updated 4-string constructor if no profile exists yet
            UserProfile profile;
            try {
                profile = userProfileClient.getProfile(userId);
            } catch (HttpClientErrorException.NotFound ex) {
                profile = new UserProfile("", "", "", ""); 
            }

            User user = new User(userId, userAccountResponse.getName(), userAccountResponse.getEmail(), profile, loginResponse.getToken());

            return new GatewayResponse(200, true, "Credentials verified and secure handshake completed by gateway.", user);
        } catch (HttpClientErrorException ex) {
            String errorMsg = getErrorMessage(ex);
            return new GatewayResponse(ex.getStatusCode().value(), false, errorMsg);
        } catch (Exception ex) {
            return new GatewayResponse(500, false, "Failed to log in user: " + ex.getMessage());
        }
    }

    @Override
    public GatewayResponse getProfile(String token) {
        if (token == null || token.trim().isEmpty()) {
            return new GatewayResponse(400, false, "Authorization token must be supplied.");
        }

        try {
            UserAccountResponse userAccount = authenticationClient.getUser(token);
            String userId = userAccount.getId();

            UserProfile profile;
            try {
                profile = userProfileClient.getProfile(userId);
            } catch (HttpClientErrorException.NotFound ex) {
                // Creates a blank 4-field profile if missing downstream
                UserProfile initialProfile = new UserProfile("", "", "", "");
                profile = userProfileClient.createOrUpdateProfile(userId, initialProfile);
            }

            User user = new User(userId, userAccount.getName(), userAccount.getEmail(), profile);
            return new GatewayResponse(200, true, "User profile retrieved successfully from the gateway.", user);
        } catch (HttpClientErrorException ex) {
            String errorMsg = getErrorMessage(ex);
            return new GatewayResponse(ex.getStatusCode().value(), false, errorMsg);
        } catch (Exception ex) {
            return new GatewayResponse(500, false, "Failed to retrieve user profile: " + ex.getMessage());
        }
    }

    @Override
    public GatewayResponse updateProfile(UserProfile profile, String token) {
        if (profile == null) {
            return new GatewayResponse(400, false, "Profile body is required.");
        }

        if (token == null || token.trim().isEmpty()) {
            return new GatewayResponse(400, false, "Token is required.");
        }

        try {
            UserAccountResponse userAccountResponse = authenticationClient.getUser(token);
            UserProfile updatedProfile = userProfileClient.createOrUpdateProfile(userAccountResponse.getId(), profile);

            User user = new User(userAccountResponse.getId(), userAccountResponse.getName(), userAccountResponse.getEmail(), updatedProfile);
            return new GatewayResponse(200, true, "User profile updated successfully in gateway and profile service.", user);
        } catch (HttpClientErrorException ex) {
            String errorMsg = getErrorMessage(ex);
            return new GatewayResponse(ex.getStatusCode().value(), false, errorMsg);
        } catch (Exception ex) {
            return new GatewayResponse(500, false, "Failed to update profile via profile service: " + ex.getMessage());
        }
    }

    private String getErrorMessage(HttpClientErrorException ex) {
        try {
            String body = ex.getResponseBodyAsString();
            if (body != null && !body.trim().isEmpty()) {
                Map<String, Object> map = objectMapper.readValue(body, Map.class);
                if (map.containsKey("message")) {
                    return (String) map.get("message");
                }
            }
        } catch (Exception ignored) {
        }
        return ex.getMessage();
    }
}
package com.jobseekercopilot.usermanagementgateway.service;

import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.jobseekercopilot.generated.authenticationservice.api.AuthenticationApi;
import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.model.LoginRequest;
import com.jobseekercopilot.usermanagementgateway.model.RegisterRequest;
import com.jobseekercopilot.usermanagementgateway.model.User;
import com.jobseekercopilot.usermanagementgateway.model.UserProfile;
import com.jobseekercopilot.usermanagementgateway.model.Qualification;
import com.jobseekercopilot.usermanagementgateway.model.Role;

@Service
@Primary
public class UserManagementService implements IUserManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);

    @Autowired
    private AuthenticationApi authenticationApi;

    @Autowired
    private UserProfilesApi userProfilesApi;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public GatewayResponse register(RegisterRequest request) {
        long startedAt = System.nanoTime();
        log.info("user-management-gateway registration received hasProfile={}",
                request != null && request.getProfile() != null);
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
            long registerStartedAt = System.nanoTime();
            log.info("Calling authentication-service register");
            authenticationApi.register(new com.jobseekercopilot.generated.authenticationservice.model.RegisterRequest()
                    .name(name).email(email).password(password));
            log.info("authentication-service register returned durationMs={}",
                    (System.nanoTime() - registerStartedAt) / 1_000_000);

            long loginStartedAt = System.nanoTime();
            log.info("Calling authentication-service login after registration");
            var loginResponse = authenticationApi.login(
                    new com.jobseekercopilot.generated.authenticationservice.model.LoginRequest()
                            .email(email).password(password));
            log.info("authentication-service login returned durationMs={}",
                    (System.nanoTime() - loginStartedAt) / 1_000_000);
            var userAccountResponse = getUser(loginResponse.getToken());
            
            UserProfile initialProfile = request.getProfile() != null ? request.getProfile() : new UserProfile(
            java.util.Collections.<String>emptyList(), // skills: List<String>
            java.util.Collections.<Qualification>emptyList(), // qualifications: List<Qualification>
            java.util.Collections.<Role>emptyList(), // roles: List<Role>
            null, // aspirations
            null  // workPreferences
        );
            UserProfile userProfile = createOrUpdateProfile(userAccountResponse.getId(), initialProfile);

            User user = new User(userAccountResponse.getId(), name, email, userProfile, loginResponse.getToken());
            log.info("user-management-gateway registration completed userId={} durationMs={}",
                    userAccountResponse.getId(),
                    (System.nanoTime() - startedAt) / 1_000_000);

            return new GatewayResponse(201, true, "Claimant account registered securely with the User Management Gateway.", user);
        } catch (HttpClientErrorException ex) {
            log.warn("user-management-gateway registration failed status={} durationMs={}",
                    ex.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            String errorMsg = getErrorMessage(ex);
            return new GatewayResponse(ex.getStatusCode().value(), false, errorMsg);
        } catch (Exception ex) {
            log.error("user-management-gateway registration failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName(),
                    ex);
            return new GatewayResponse(500, false, "Failed to register user: " + ex.getMessage());
        }
    }

    @Override
    public GatewayResponse login(LoginRequest request) {
        long startedAt = System.nanoTime();
        log.info("user-management-gateway login received hasRequest={}", request != null);
        if (request == null) {
            return new GatewayResponse(400, false, "Missing credentials.");
        }

        String email = request.getEmail();
        String password = request.getPassword();

        if (email == null || password == null || email.trim().isEmpty() || password.isEmpty()) {
            return new GatewayResponse(400, false, "Missing credentials. Both email and password must be supplied.");
        }

        try {
            long loginStartedAt = System.nanoTime();
            log.info("Calling authentication-service login");
            var loginResponse = authenticationApi.login(
                    new com.jobseekercopilot.generated.authenticationservice.model.LoginRequest()
                            .email(email).password(password));
            log.info("authentication-service login returned durationMs={}",
                    (System.nanoTime() - loginStartedAt) / 1_000_000);
            var userAccountResponse = getUser(loginResponse.getToken());
            String userId = userAccountResponse.getId();

            // Resilient fallback using the updated 4-string constructor if no profile exists yet
            UserProfile profile;
            try {
                profile = getProfileByUserId(userId);
            } catch (HttpClientErrorException.NotFound ex) {
                profile = new UserProfile(
                    java.util.Collections.<String>emptyList(),
                    java.util.Collections.<Qualification>emptyList(),
                    java.util.Collections.<Role>emptyList(),
                    null,
                    null
                );
            }

            User user = new User(userId, userAccountResponse.getName(), userAccountResponse.getEmail(), profile, loginResponse.getToken());
            log.info("user-management-gateway login completed userId={} durationMs={}",
                    userId,
                    (System.nanoTime() - startedAt) / 1_000_000);

            return new GatewayResponse(200, true, "Credentials verified and secure handshake completed by gateway.", user);
        } catch (HttpClientErrorException ex) {
            log.warn("user-management-gateway login failed status={} durationMs={}",
                    ex.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            String errorMsg = getErrorMessage(ex);
            return new GatewayResponse(ex.getStatusCode().value(), false, errorMsg);
        } catch (Exception ex) {
            log.error("user-management-gateway login failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName(),
                    ex);
            return new GatewayResponse(500, false, "Failed to log in user: " + ex.getMessage());
        }
    }

    @Override
    public GatewayResponse getProfile(String token) {
        long startedAt = System.nanoTime();
        log.info("user-management-gateway profile request received hasToken={}", token != null && !token.isBlank());
        if (token == null || token.trim().isEmpty()) {
            return new GatewayResponse(400, false, "Authorization token must be supplied.");
        }

        try {
            var userAccount = getUser(token);
            String userId = userAccount.getId();

            UserProfile profile;
            try {
                profile = getProfileByUserId(userId);
            } catch (HttpClientErrorException.NotFound ex) {
                // Creates a blank 4-field profile if missing downstream
                UserProfile initialProfile = new UserProfile(
                java.util.Collections.<String>emptyList(),
                java.util.Collections.<Qualification>emptyList(),
                java.util.Collections.<Role>emptyList(),
                null,
                null
);
                profile = createOrUpdateProfile(userId, initialProfile);
            }

            User user = new User(userId, userAccount.getName(), userAccount.getEmail(), profile);
            log.info("user-management-gateway profile request completed userId={} durationMs={}",
                    userId,
                    (System.nanoTime() - startedAt) / 1_000_000);
            return new GatewayResponse(200, true, "User profile retrieved successfully from the gateway.", user);
        } catch (HttpClientErrorException ex) {
            log.warn("user-management-gateway profile request failed status={} durationMs={}",
                    ex.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            String errorMsg = getErrorMessage(ex);
            return new GatewayResponse(ex.getStatusCode().value(), false, errorMsg);
        } catch (Exception ex) {
            log.error("user-management-gateway profile request failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName(),
                    ex);
            return new GatewayResponse(500, false, "Failed to retrieve user profile: " + ex.getMessage());
        }
    }

    @Override
    public GatewayResponse updateProfile(UserProfile profile, String token) {
        long startedAt = System.nanoTime();
        log.info("user-management-gateway profile update received skillsCount={} qualificationsCount={} rolesCount={}",
                profile == null || profile.getSkills() == null ? 0 : profile.getSkills().size(),
                profile == null || profile.getQualifications() == null ? 0 : profile.getQualifications().size(),
                profile == null || profile.getRoles() == null ? 0 : profile.getRoles().size());
        if (profile == null) {
            return new GatewayResponse(400, false, "Profile body is required.");
        }

        if (token == null || token.trim().isEmpty()) {
            return new GatewayResponse(400, false, "Token is required.");
        }

        try {
            var userAccountResponse = getUser(token);
            UserProfile updatedProfile = createOrUpdateProfile(userAccountResponse.getId(), profile);

            User user = new User(userAccountResponse.getId(), userAccountResponse.getName(), userAccountResponse.getEmail(), updatedProfile);
            log.info("user-management-gateway profile update completed userId={} durationMs={}",
                    userAccountResponse.getId(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return new GatewayResponse(200, true, "User profile updated successfully in gateway and profile service.", user);
        } catch (HttpClientErrorException ex) {
            log.warn("user-management-gateway profile update failed status={} durationMs={}",
                    ex.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            String errorMsg = getErrorMessage(ex);
            return new GatewayResponse(ex.getStatusCode().value(), false, errorMsg);
        } catch (Exception ex) {
            log.error("user-management-gateway profile update failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName(),
                    ex);
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

    private com.jobseekercopilot.generated.authenticationservice.model.UserAccountResponse getUser(
            String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }
        String cleanToken = token.startsWith("Bearer ") ? token.substring(7) : token;
        long startedAt = System.nanoTime();
        log.info("Calling authentication-service current user");
        var response = authenticationApi.getCurrentUser("Bearer " + cleanToken);
        log.info("authentication-service current user returned userId={} durationMs={}",
                response == null ? null : response.getId(),
                (System.nanoTime() - startedAt) / 1_000_000);
        return response;
    }

    private UserProfile getProfileByUserId(String userId) {
        long startedAt = System.nanoTime();
        log.info("Calling user-profile-service get profile userId={}", userId);
        var downstream = userProfilesApi.getMyProfile(userId);
        UserProfile profile = objectMapper.convertValue(downstream, UserProfile.class);
        log.info("user-profile-service get profile returned userId={} durationMs={}",
                userId,
                (System.nanoTime() - startedAt) / 1_000_000);
        return profile;
    }

    private UserProfile createOrUpdateProfile(String userId, UserProfile profile) {
        long startedAt = System.nanoTime();
        log.info("Calling user-profile-service save profile userId={} skillsCount={} qualificationsCount={} rolesCount={}",
                userId,
                profile == null || profile.getSkills() == null ? 0 : profile.getSkills().size(),
                profile == null || profile.getQualifications() == null ? 0 : profile.getQualifications().size(),
                profile == null || profile.getRoles() == null ? 0 : profile.getRoles().size());
        var downstreamRequest = objectMapper.convertValue(
                profile,
                com.jobseekercopilot.generated.userprofileservice.model.UserProfile.class);
        var downstreamResponse = userProfilesApi.createOrUpdateMyProfile(userId, downstreamRequest);
        UserProfile savedProfile = objectMapper.convertValue(downstreamResponse, UserProfile.class);
        log.info("user-profile-service save profile returned userId={} durationMs={}",
                userId,
                (System.nanoTime() - startedAt) / 1_000_000);
        return savedProfile;
    }
}

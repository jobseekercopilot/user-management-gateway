package com.jobseekercopilot.usermanagementgateway.service;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.generated.authenticationservice.api.AuthenticationApi;
import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import com.jobseekercopilot.usermanagementgateway.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private AuthenticationApi authenticationApi;

    @Mock
    private UserProfilesApi userProfilesApi;

    @InjectMocks
    private UserManagementService userManagementService;

    @Test
    void register_ShouldReturnSuccess() {
        RegisterRequest request = new RegisterRequest();
        request.setName("John Doe");
        request.setEmail("john@test.com");
        request.setPassword("A valid local passphrase 2026!");
        request.setProfile(new UserProfile(
            List.of("Java"),
            List.of(new Qualification()),
            List.of(new Role()),
            new Aspirations(),
            new WorkPreferences()
        ));

        var loginResponse = new com.jobseekercopilot.generated.authenticationservice.model.LoginResponse()
                .token("jwt-token").refreshToken("refresh-token").expiresIn(900L);
        var accountResponse = new com.jobseekercopilot.generated.authenticationservice.model.UserAccountResponse()
                .id("user-123").name("John Doe").email("john@test.com");
        UserProfile profile = new UserProfile(
            List.of("Java"),
            List.of(new Qualification()),
            List.of(new Role()),
            new Aspirations(),
            new WorkPreferences()
        );

        when(authenticationApi.login(any())).thenReturn(loginResponse);
        when(authenticationApi.getCurrentUser("Bearer jwt-token")).thenReturn(accountResponse);
        when(userProfilesApi.createOrUpdateMyProfile(eq("Bearer jwt-token"), any()))
                .thenReturn(downstreamProfile(profile));

        GatewayResponse response = userManagementService.register(request);

        assertTrue(response.isSuccess(), response.getMessage());
        assertEquals(201, response.getStatusCode());
        assertNotNull(response.getUser());
    }

    @Test
    void register_ShouldReturnBadRequest_WhenNameTooShort() {
        RegisterRequest request = new RegisterRequest();
        request.setName("A");

        GatewayResponse response = userManagementService.register(request);

        assertFalse(response.isSuccess());
        assertEquals(400, response.getStatusCode());
        verify(authenticationApi, never()).register(any());
    }

    @Test
    void login_ShouldReturnSuccess() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@test.com");
        request.setPassword("A valid local passphrase 2026!");

        var loginResponse = new com.jobseekercopilot.generated.authenticationservice.model.LoginResponse()
                .token("jwt-token").refreshToken("refresh-token").expiresIn(900L);
        var accountResponse = new com.jobseekercopilot.generated.authenticationservice.model.UserAccountResponse()
                .id("user-123").name("John Doe").email("john@test.com");
        UserProfile profile = new UserProfile(
            List.of("Java"),
            List.of(new Qualification()),
            List.of(new Role()),
            new Aspirations(),
            new WorkPreferences()
        );

        when(authenticationApi.login(any())).thenReturn(loginResponse);
        when(authenticationApi.getCurrentUser("Bearer jwt-token")).thenReturn(accountResponse);
        when(userProfilesApi.getMyProfile("Bearer jwt-token")).thenReturn(downstreamProfile(profile));

        GatewayResponse response = userManagementService.login(request);

        assertTrue(response.isSuccess(), response.getMessage());
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getUser());
    }

    @Test
    void login_ShouldReturnBadRequest_WhenMissingCredentials() {
        LoginRequest request = new LoginRequest();
        request.setEmail("");

        GatewayResponse response = userManagementService.login(request);

        assertFalse(response.isSuccess());
        assertEquals(400, response.getStatusCode());
        verify(authenticationApi, never()).login(any());
    }

    @Test
    void login_ReturnsSafeServiceUnavailable_WhenAuthenticationIsUnavailable() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@test.com");
        request.setPassword("A valid local passphrase 2026!");
        when(authenticationApi.login(any()))
                .thenThrow(new ResourceAccessException("connection details must not leak"));

        GatewayResponse response = userManagementService.login(request);

        assertFalse(response.isSuccess());
        assertEquals(503, response.getStatusCode());
        assertEquals("A required service is temporarily unavailable.", response.getMessage());
        assertFalse(response.getMessage().contains("connection details"));
    }

    @Test
    void login_MapsDownstreamAuthenticationFailureWithoutLeakingCause() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@test.com");
        request.setPassword("A valid local passphrase 2026!");
        when(authenticationApi.login(any()))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "downstream secret detail"));

        GatewayResponse response = userManagementService.login(request);

        assertEquals(401, response.getStatusCode());
        assertEquals("AUTHENTICATION_FAILED", response.getError().code());
        assertEquals("Invalid email or password.", response.getMessage());
        assertFalse(response.getMessage().contains("secret"));
    }

    @Test
    void login_MapsUnexpectedFailureWithoutLeakingCause() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@test.com");
        request.setPassword("A valid local passphrase 2026!");
        when(authenticationApi.login(any())).thenThrow(new IllegalStateException("database secret detail"));

        GatewayResponse response = userManagementService.login(request);

        assertEquals(500, response.getStatusCode());
        assertEquals("INTERNAL_ERROR", response.getError().code());
        assertEquals("An unexpected error occurred.", response.getMessage());
    }

    @Test
    void passwordResetRequestReturnsTheApprovedGenericResponse() {
        GatewayResponse response = userManagementService.requestPasswordReset(
                new PasswordResetRequest("person@example.test"));

        assertEquals(202, response.getStatusCode());
        assertTrue(response.isSuccess());
        assertEquals(
                "If an account exists for that email, a password-reset link has been sent.",
                response.getMessage());
        verify(authenticationApi).requestPasswordReset(any());
    }

    @Test
    void passwordResetRequestDoesNotCallDownstreamForInvalidEmail() {
        GatewayResponse response = userManagementService.requestPasswordReset(
                new PasswordResetRequest("not-an-email"));

        assertEquals(400, response.getStatusCode());
        verify(authenticationApi, never()).requestPasswordReset(any());
    }

    @Test
    void passwordResetCompletionReturnsSuccessWithoutReflectingToken() {
        String token = "A".repeat(43);
        GatewayResponse response = userManagementService.completePasswordReset(
                new PasswordResetCompletionRequest(
                        token, "A secure replacement passphrase 2026!"));

        assertEquals(200, response.getStatusCode());
        assertTrue(response.isSuccess());
        assertFalse(response.getMessage().contains(token));
        verify(authenticationApi).completePasswordReset(any());
    }

    @Test
    void passwordResetCompletionMapsDownstreamRejectionToStableSafeError() {
        doThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "private reset detail",
                org.springframework.http.HttpHeaders.EMPTY, new byte[0],
                java.nio.charset.StandardCharsets.UTF_8))
                .when(authenticationApi).completePasswordReset(any());

        GatewayResponse response = userManagementService.completePasswordReset(
                new PasswordResetCompletionRequest(
                        "A".repeat(43), "A secure replacement passphrase 2026!"));

        assertEquals(400, response.getStatusCode());
        assertEquals("PASSWORD_RESET_REJECTED", response.getError().code());
        assertFalse(response.getMessage().contains("private"));
    }

    @Test
    void register_ReturnsServiceUnavailable_WhenProfileFailsAfterAuthentication() {
        RegisterRequest request = new RegisterRequest();
        request.setName("John Doe");
        request.setEmail("john@test.com");
        request.setPassword("A valid local passphrase 2026!");
        var loginResponse = new com.jobseekercopilot.generated.authenticationservice.model.LoginResponse()
                .token("jwt-token").refreshToken("refresh-token").expiresIn(900L);
        var accountResponse = new com.jobseekercopilot.generated.authenticationservice.model.UserAccountResponse()
                .id("user-123").name("John Doe").email("john@test.com");
        when(authenticationApi.login(any())).thenReturn(loginResponse);
        when(authenticationApi.getCurrentUser("Bearer jwt-token")).thenReturn(accountResponse);
        when(userProfilesApi.createOrUpdateMyProfile(eq("Bearer jwt-token"), any()))
                .thenThrow(new ResourceAccessException("profile unavailable"));

        GatewayResponse response = userManagementService.register(request);

        assertFalse(response.isSuccess());
        assertEquals(503, response.getStatusCode());
        assertEquals("A required service is temporarily unavailable.", response.getMessage());
    }

    @Test
    void getProfile_ShouldReturnSuccess() {
        String token = "valid-token";
        var accountResponse = new com.jobseekercopilot.generated.authenticationservice.model.UserAccountResponse()
                .id("user-123").name("John Doe").email("john@test.com");
        UserProfile profile = new UserProfile(
            List.of("Java"),
            List.of(new Qualification()),
            List.of(new Role()),
            new Aspirations(),
            new WorkPreferences()
        );

        when(authenticationApi.getCurrentUser("Bearer " + token)).thenReturn(accountResponse);
        when(userProfilesApi.getMyProfile("Bearer valid-token")).thenReturn(downstreamProfile(profile));

        GatewayResponse response = userManagementService.getProfile(token);

        assertTrue(response.isSuccess(), response.getMessage());
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getUser());
    }

    @Test
    void getProfile_ShouldReturnBadRequest_WhenTokenMissing() {
        GatewayResponse response = userManagementService.getProfile(null);

        assertFalse(response.isSuccess());
        assertEquals(400, response.getStatusCode());
    }

    @Test
    void updateProfile_ShouldReturnSuccess() {
        String token = "valid-token";
        UserProfile profile = new UserProfile(
            List.of("Kotlin"),
            List.of(new Qualification()),
            List.of(new Role()),
            new Aspirations(),
            new WorkPreferences()
        );
        var accountResponse = new com.jobseekercopilot.generated.authenticationservice.model.UserAccountResponse()
                .id("user-123").name("John Doe").email("john@test.com");

        when(authenticationApi.getCurrentUser("Bearer " + token)).thenReturn(accountResponse);
        when(userProfilesApi.createOrUpdateMyProfile(eq("Bearer valid-token"), any()))
                .thenReturn(downstreamProfile(profile));

        GatewayResponse response = userManagementService.updateProfile(profile, token);

        assertTrue(response.isSuccess(), response.getMessage());
        assertEquals(200, response.getStatusCode());
    }

    @Test
    void updateProfile_ShouldReturnBadRequest_WhenProfileNull() {
        GatewayResponse response = userManagementService.updateProfile(null, "token");

        assertFalse(response.isSuccess());
        assertEquals(400, response.getStatusCode());
    }

    private com.jobseekercopilot.generated.userprofileservice.model.UserProfile downstreamProfile(
            UserProfile profile) {
        return new ObjectMapper().convertValue(
                profile,
                com.jobseekercopilot.generated.userprofileservice.model.UserProfile.class);
    }
}

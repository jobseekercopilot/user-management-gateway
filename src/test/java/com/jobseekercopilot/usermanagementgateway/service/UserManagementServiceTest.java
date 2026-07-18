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
        request.setPassword("password123");
        request.setProfile(new UserProfile(
            List.of("Java"),
            List.of(new Qualification()),
            List.of(new Role()),
            new Aspirations(),
            new WorkPreferences()
        ));

        var loginResponse = new com.jobseekercopilot.generated.authenticationservice.model.LoginResponse()
                .token("jwt-token");
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
        when(userProfilesApi.createOrUpdateMyProfile(eq("user-123"), any()))
                .thenReturn(downstreamProfile(profile));

        GatewayResponse response = userManagementService.register(request);

        assertTrue(response.isSuccess(), response.getMessage());
        assertEquals(201, response.getStatusCode());
        assertNotNull(response.getUser());
        assertEquals("jwt-token", response.getUser().getToken());
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
        request.setPassword("password123");

        var loginResponse = new com.jobseekercopilot.generated.authenticationservice.model.LoginResponse()
                .token("jwt-token");
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
        when(userProfilesApi.getMyProfile("user-123")).thenReturn(downstreamProfile(profile));

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
        when(userProfilesApi.getMyProfile("user-123")).thenReturn(downstreamProfile(profile));

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
        when(userProfilesApi.createOrUpdateMyProfile(eq("user-123"), any()))
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

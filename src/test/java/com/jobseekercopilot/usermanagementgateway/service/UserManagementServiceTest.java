package com.jobseekercopilot.usermanagementgateway.service;

import com.jobseekercopilot.usermanagementgateway.client.AuthenticationClient;
import com.jobseekercopilot.usermanagementgateway.client.UserProfileClient;
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
    private AuthenticationClient authenticationClient;

    @Mock
    private UserProfileClient userProfileClient;

    @InjectMocks
    private UserManagementService userManagementService;

    @Test
    void register_ShouldReturnSuccess() {
        RegisterRequest request = new RegisterRequest();
        request.setName("John Doe");
        request.setEmail("john@test.com");
        request.setPassword("password123");
        request.setProfile(new UserProfile("Java", "5 years", "Lead", "Remote"));

        LoginResponse loginResponse = new LoginResponse("jwt-token");
        UserAccountResponse accountResponse = new UserAccountResponse("user-123", "John Doe", "john@test.com");
        UserProfile profile = new UserProfile("Java", "5 years", "Lead", "Remote");

        doNothing().when(authenticationClient).register("John Doe", "john@test.com", "password123");
        when(authenticationClient.login("john@test.com", "password123")).thenReturn(loginResponse);
        when(authenticationClient.getUser("jwt-token")).thenReturn(accountResponse);
        when(userProfileClient.createOrUpdateProfile("user-123", request.getProfile())).thenReturn(profile);

        GatewayResponse response = userManagementService.register(request);

        assertTrue(response.isSuccess());
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
        verify(authenticationClient, never()).register(any(), any(), any());
    }

    @Test
    void login_ShouldReturnSuccess() {
        LoginRequest request = new LoginRequest();
        request.setEmail("john@test.com");
        request.setPassword("password123");

        LoginResponse loginResponse = new LoginResponse("jwt-token");
        UserAccountResponse accountResponse = new UserAccountResponse("user-123", "John Doe", "john@test.com");
        UserProfile profile = new UserProfile("Java", "5 years", "Lead", "Remote");

        when(authenticationClient.login("john@test.com", "password123")).thenReturn(loginResponse);
        when(authenticationClient.getUser("jwt-token")).thenReturn(accountResponse);
        when(userProfileClient.getProfile("user-123")).thenReturn(profile);

        GatewayResponse response = userManagementService.login(request);

        assertTrue(response.isSuccess());
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
        verify(authenticationClient, never()).login(any(), any());
    }

    @Test
    void getProfile_ShouldReturnSuccess() {
        String token = "valid-token";
        UserAccountResponse accountResponse = new UserAccountResponse("user-123", "John Doe", "john@test.com");
        UserProfile profile = new UserProfile("Java", "5 years", "Lead", "Remote");

        when(authenticationClient.getUser(token)).thenReturn(accountResponse);
        when(userProfileClient.getProfile("user-123")).thenReturn(profile);

        GatewayResponse response = userManagementService.getProfile(token);

        assertTrue(response.isSuccess());
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
        UserProfile profile = new UserProfile("Kotlin", "3 years", "Senior", "Remote");
        UserAccountResponse accountResponse = new UserAccountResponse("user-123", "John Doe", "john@test.com");

        when(authenticationClient.getUser(token)).thenReturn(accountResponse);
        when(userProfileClient.createOrUpdateProfile("user-123", profile)).thenReturn(profile);

        GatewayResponse response = userManagementService.updateProfile(profile, token);

        assertTrue(response.isSuccess());
        assertEquals(200, response.getStatusCode());
    }

    @Test
    void updateProfile_ShouldReturnBadRequest_WhenProfileNull() {
        GatewayResponse response = userManagementService.updateProfile(null, "token");

        assertFalse(response.isSuccess());
        assertEquals(400, response.getStatusCode());
    }
}
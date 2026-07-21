package com.jobseekercopilot.usermanagementgateway.controller;

import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.model.LoginRequest;
import com.jobseekercopilot.usermanagementgateway.model.RegisterRequest;
import com.jobseekercopilot.usermanagementgateway.model.UserProfile;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry.UserOperation;
import com.jobseekercopilot.usermanagementgateway.service.UserManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementControllerTest {

    @Mock
    private UserManagementService userManagementService;

    @Mock
    private GatewayTelemetry telemetry;

    @InjectMocks
    private UserManagementController userManagementController;

    @Test
    void register_ShouldReturnResponse() {
        RegisterRequest request = new RegisterRequest();
        GatewayResponse serviceResponse = new GatewayResponse(201, true, "Registered");
        when(userManagementService.register(request)).thenReturn(serviceResponse);

        ResponseEntity<GatewayResponse> response = userManagementController.register(request);

        assertEquals(201, response.getStatusCodeValue());
        assertTrue(response.getBody().isSuccess());
        verify(userManagementService, times(1)).register(request);
        verify(telemetry).record(eq(UserOperation.REGISTER), same(serviceResponse), anyLong());
    }

    @Test
    void login_ShouldReturnResponse() {
        LoginRequest request = new LoginRequest();
        GatewayResponse serviceResponse = new GatewayResponse(200, true, "Logged in");
        when(userManagementService.login(request)).thenReturn(serviceResponse);

        ResponseEntity<GatewayResponse> response = userManagementController.login(request);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isSuccess());
        verify(userManagementService, times(1)).login(request);
        verify(telemetry).record(eq(UserOperation.LOGIN), same(serviceResponse), anyLong());
    }

    @Test
    void getProfile_ShouldReturnResponse() {
        String token = "Bearer valid-token";
        GatewayResponse serviceResponse = new GatewayResponse(200, true, "Profile retrieved");
        when(userManagementService.getProfile(token)).thenReturn(serviceResponse);

        ResponseEntity<GatewayResponse> response = userManagementController.getProfile("test@test.com", token);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isSuccess());
        verify(userManagementService, times(1)).getProfile(token);
        verify(telemetry).record(eq(UserOperation.PROFILE_READ), same(serviceResponse), anyLong());
    }

    @Test
    void updateProfile_ShouldReturnResponse() {
        String token = "Bearer valid-token";
        UserProfile profile = new UserProfile();
        GatewayResponse serviceResponse = new GatewayResponse(200, true, "Profile updated");
        when(userManagementService.updateProfile(profile, token)).thenReturn(serviceResponse);

        ResponseEntity<GatewayResponse> response = userManagementController.updateProfile("test@test.com", profile, token);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isSuccess());
        verify(userManagementService, times(1)).updateProfile(profile, token);
        verify(telemetry).record(eq(UserOperation.PROFILE_UPDATE), same(serviceResponse), anyLong());
    }
}

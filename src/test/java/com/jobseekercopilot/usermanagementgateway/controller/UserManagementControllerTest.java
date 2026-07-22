package com.jobseekercopilot.usermanagementgateway.controller;

import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.model.LoginRequest;
import com.jobseekercopilot.usermanagementgateway.model.RegisterRequest;
import com.jobseekercopilot.usermanagementgateway.model.UserProfile;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry.UserOperation;
import com.jobseekercopilot.usermanagementgateway.model.SessionOutcome;
import com.jobseekercopilot.usermanagementgateway.security.RefreshCoordinator;
import com.jobseekercopilot.usermanagementgateway.security.SessionCookieService;
import com.jobseekercopilot.usermanagementgateway.service.UserManagementService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementControllerTest {

    @Mock
    private UserManagementService userManagementService;

    @Mock
    private GatewayTelemetry telemetry;

    @Mock
    private SessionCookieService sessionCookies;

    @Mock
    private RefreshCoordinator refreshCoordinator;

    @InjectMocks
    private UserManagementController userManagementController;

    @Test
    void register_ShouldReturnResponse() {
        RegisterRequest request = new RegisterRequest();
        GatewayResponse serviceResponse = new GatewayResponse(201, true, "Registered");
        SessionOutcome session = new SessionOutcome(serviceResponse, "access", "refresh", 900);
        when(userManagementService.registerSession(request)).thenReturn(session);
        when(sessionCookies.withSession(any(), same(serviceResponse), same(session)))
                .thenReturn(ResponseEntity.status(201).body(serviceResponse));

        ResponseEntity<GatewayResponse> response = userManagementController.register(request);

        assertEquals(201, response.getStatusCodeValue());
        assertTrue(response.getBody().isSuccess());
        verify(userManagementService, times(1)).registerSession(request);
        verify(telemetry).record(eq(UserOperation.REGISTER), same(serviceResponse), anyLong());
    }

    @Test
    void login_ShouldReturnResponse() {
        LoginRequest request = new LoginRequest();
        GatewayResponse serviceResponse = new GatewayResponse(200, true, "Logged in");
        SessionOutcome session = new SessionOutcome(serviceResponse, "access", "refresh", 900);
        when(userManagementService.loginSession(request)).thenReturn(session);
        when(sessionCookies.withSession(any(), same(serviceResponse), same(session)))
                .thenReturn(ResponseEntity.ok(serviceResponse));

        ResponseEntity<GatewayResponse> response = userManagementController.login(request);

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isSuccess());
        verify(userManagementService, times(1)).loginSession(request);
        verify(telemetry).record(eq(UserOperation.LOGIN), same(serviceResponse), anyLong());
    }

    @Test
    void getProfile_ShouldReturnResponse() {
        String token = "valid-token";
        GatewayResponse serviceResponse = new GatewayResponse(200, true, "Profile retrieved");
        when(userManagementService.getProfile(token)).thenReturn(serviceResponse);

        ResponseEntity<GatewayResponse> response = userManagementController.getProfile(authentication("valid-token"));

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isSuccess());
        verify(userManagementService, times(1)).getProfile(token);
        verify(telemetry).record(eq(UserOperation.PROFILE_READ), same(serviceResponse), anyLong());
    }

    @Test
    void updateProfile_ShouldReturnResponse() {
        String token = "valid-token";
        UserProfile profile = new UserProfile();
        GatewayResponse serviceResponse = new GatewayResponse(200, true, "Profile updated");
        when(userManagementService.updateProfile(profile, token)).thenReturn(serviceResponse);

        ResponseEntity<GatewayResponse> response = userManagementController.updateProfile(
                profile, authentication("valid-token"));

        assertEquals(200, response.getStatusCodeValue());
        assertTrue(response.getBody().isSuccess());
        verify(userManagementService, times(1)).updateProfile(profile, token);
        verify(telemetry).record(eq(UserOperation.PROFILE_UPDATE), same(serviceResponse), anyLong());
    }

    private BearerTokenAuthentication authentication(String token) {
        var principal = new DefaultOAuth2AuthenticatedPrincipal(
                "user-123", java.util.Map.of("sub", "user-123"),
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER, token,
                java.time.Instant.now(), java.time.Instant.now().plusSeconds(60));
        return new BearerTokenAuthentication(principal, accessToken, principal.getAuthorities());
    }
}

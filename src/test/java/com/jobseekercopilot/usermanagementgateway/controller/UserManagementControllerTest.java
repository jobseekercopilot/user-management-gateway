package com.jobseekercopilot.usermanagementgateway.controller;

import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.model.LoginRequest;
import com.jobseekercopilot.usermanagementgateway.model.RegisterRequest;
import com.jobseekercopilot.usermanagementgateway.model.RegistrationLegalRequirements;
import com.jobseekercopilot.usermanagementgateway.model.ProfessionalContact;
import com.jobseekercopilot.usermanagementgateway.model.User;
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
    void registrationRequirements_ReturnsAuthoritativeNoStoreResponse() {
        RegistrationLegalRequirements requirements = new RegistrationLegalRequirements(
                "2026-08-15",
                18,
                "https://jobseekercopilot.com/terms",
                "https://jobseekercopilot.com/privacy");
        when(userManagementService.getRegistrationLegalRequirements())
                .thenReturn(requirements);

        ResponseEntity<RegistrationLegalRequirements> response =
                userManagementController.registrationRequirements();

        assertEquals(200, response.getStatusCodeValue());
        assertSame(requirements, response.getBody());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
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

    @Test
    void updateProfessionalContact_ForwardsSessionAndRevisionAndReturnsNewEtag() {
        ProfessionalContact contact = new ProfessionalContact();
        UserProfile profile = new UserProfile();
        profile.setRevision(4L);
        profile.setProfessionalContact(contact);
        GatewayResponse serviceResponse = new GatewayResponse(
                200,
                true,
                "Professional contact updated successfully.",
                new User("user-123", "Example User", "user@example.test", profile));
        when(userManagementService.updateProfessionalContact(
                contact, "valid-token", "\"3\""))
                .thenReturn(serviceResponse);

        ResponseEntity<GatewayResponse> response =
                userManagementController.updateProfessionalContact(
                        contact, "\"3\"", authentication("valid-token"));

        assertEquals(200, response.getStatusCodeValue());
        assertSame(serviceResponse, response.getBody());
        assertEquals("\"4\"", response.getHeaders().getETag());
        verify(userManagementService).updateProfessionalContact(
                contact, "valid-token", "\"3\"");
    }

    @Test
    void accountExportIsNoStoreAndUsesTheAuthenticatedAccessToken() {
        var export = new com.jobseekercopilot.generated.authenticationservice.model.PersonalDataExport()
                .schemaVersion("job-seeker-copilot-personal-data.v3")
                .payments(java.util.Map.of("schemaVersion", "payment-export-v1"));
        when(userManagementService.exportPersonalData("valid-token"))
                .thenReturn(export);

        var response = userManagementController.exportPersonalData(
                authentication("valid-token"));

        assertEquals(200, response.getStatusCodeValue());
        assertSame(export, response.getBody());
        assertTrue(response.getHeaders().getCacheControl().contains("no-store"));
        assertEquals(
                "attachment; filename=\"job-seeker-copilot-personal-data.json\"",
                response.getHeaders().getFirst(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION));
        verify(userManagementService).exportPersonalData("valid-token");
    }

    @Test
    void acceptedAccountDeletionClearsBrowserCredentials() {
        var deletion = new com.jobseekercopilot.generated.authenticationservice.model.AccountDeletionResponse()
                .operationId(java.util.UUID.fromString(
                        "10000000-0000-4000-8000-000000000001"));
        when(userManagementService.deleteAccount(
                        "valid-token", "delete-request-0001"))
                .thenReturn(deletion);
        when(sessionCookies.clearSession(any(), same(deletion)))
                .thenReturn(ResponseEntity.accepted().body(deletion));

        var response = userManagementController.deleteAccount(
                "delete-request-0001", authentication("valid-token"));

        assertEquals(202, response.getStatusCodeValue());
        assertSame(deletion, response.getBody());
        verify(userManagementService).deleteAccount(
                "valid-token", "delete-request-0001");
        verify(sessionCookies).clearSession(any(), same(deletion));
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

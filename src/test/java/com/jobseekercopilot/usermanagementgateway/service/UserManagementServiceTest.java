package com.jobseekercopilot.usermanagementgateway.service;

import java.util.List;
import java.util.UUID;
import java.time.LocalDate;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.generated.authenticationservice.api.AuthenticationApi;
import com.jobseekercopilot.generated.userprofileservice.api.EvidenceLibraryApi;
import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceEntry;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceSupersedeRequest;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceWriteRequest;
import com.jobseekercopilot.generated.userprofileservice.model.ProfilePreferencesUpdate;
import com.jobseekercopilot.usermanagementgateway.config.UserProfileAccessTokenContext;
import com.jobseekercopilot.usermanagementgateway.model.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserManagementServiceTest {

    @Mock
    private AuthenticationApi authenticationApi;

    @Mock
    private UserProfilesApi userProfilesApi;

    @Mock
    private EvidenceLibraryApi evidenceLibraryApi;

    @Spy
    private UserProfileAccessTokenContext userProfileAccessTokenContext =
            new UserProfileAccessTokenContext();

    @InjectMocks
    private UserManagementService userManagementService;

    @Test
    void register_WithCredentialsOnly_CreatesBlankProfileAndReturnsSuccess() {
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
        var persistedProfile =
                new com.jobseekercopilot.generated.userprofileservice.model.UserProfile(
                        42L, "user-123", 1L, UUID.randomUUID(), "digest")
                        .skills(List.of())
                        .qualifications(List.of())
                        .roles(List.of());
        when(userProfilesApi.createOrUpdateMyProfile(any(), isNull()))
                .thenReturn(persistedProfile);

        GatewayResponse response = userManagementService.register(request);

        assertTrue(response.isSuccess(), response.getMessage());
        assertEquals(201, response.getStatusCode());
        assertNotNull(response.getUser());
        assertEquals(42L, response.getUser().getProfile().getId());
        verify(userProfilesApi).createOrUpdateMyProfile(
                argThat(candidate -> candidate.getSkills().isEmpty()
                        && candidate.getQualifications().isEmpty()
                        && candidate.getRoles().isEmpty()
                        && candidate.getAspirations() == null
                        && candidate.getWorkPreferences() == null),
                isNull());
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
    void updatePreferences_ForwardsRevisionWithoutReplacingHistory() {
        var update = new ProfilePreferencesUpdate().skills(List.of("Java"));
        var account = new com.jobseekercopilot.generated.authenticationservice.model.UserAccountResponse()
                .id("user-123").name("John Doe").email("john@test.com");
        var downstream = downstreamProfile(new UserProfile(
                List.of("Java"), List.of(), List.of(), null, null));
        when(authenticationApi.getCurrentUser("Bearer token")).thenReturn(account);
        when(userProfilesApi.updateMyPreferences(update, "\"3\""))
                .thenReturn(downstream);

        GatewayResponse response = userManagementService.updatePreferences(update, "token", "\"3\"");

        assertTrue(response.isSuccess());
        assertEquals(List.of("Java"), response.getUser().getProfile().getSkills());
        verify(userProfilesApi).updateMyPreferences(update, "\"3\"");
    }

    @Test
    void archiveEvidence_ForwardsOwnerSessionAndEntryVersion() {
        UUID entryId = UUID.randomUUID();
        EvidenceEntry body = new EvidenceEntry();
        HttpHeaders downstreamHeaders = unsafeDownstreamHeaders("\"6\"");
        ResponseEntity<EvidenceEntry> downstream =
                new ResponseEntity<>(body, downstreamHeaders, HttpStatus.ACCEPTED);
        when(evidenceLibraryApi.archiveEvidenceWithHttpInfo(
                entryId, "\"5\"")).thenReturn(downstream);

        ResponseEntity<EvidenceEntry> response =
                userManagementService.archiveEvidence("token", entryId, "\"5\"");

        assertNotSame(downstream, response);
        assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
        assertSame(body, response.getBody());
        assertEquals("\"6\"", response.getHeaders().getETag());
        assertUnsafeDownstreamHeadersWereDropped(response.getHeaders());
    }

    @Test
    void listEvidence_RebuildsResponseWithoutDownstreamTransportHeaders() {
        List<EvidenceEntry> body = List.of(new EvidenceEntry(), new EvidenceEntry());
        HttpHeaders downstreamHeaders = unsafeDownstreamHeaders("\"9\"");
        ResponseEntity<List<EvidenceEntry>> downstream =
                new ResponseEntity<>(body, downstreamHeaders, HttpStatus.PARTIAL_CONTENT);
        when(evidenceLibraryApi.listEvidenceWithHttpInfo(true)).thenReturn(downstream);

        ResponseEntity<List<EvidenceEntry>> response =
                userManagementService.listEvidence("token", true);

        assertNotSame(downstream, response);
        assertEquals(HttpStatus.PARTIAL_CONTENT, response.getStatusCode());
        assertSame(body, response.getBody());
        assertEquals("\"9\"", response.getHeaders().getETag());
        assertUnsafeDownstreamHeadersWereDropped(response.getHeaders());
    }

    @Test
    void everySingleEntryEvidenceOperation_RebuildsTheDownstreamResponse() {
        UUID entryId = UUID.randomUUID();
        String ifMatch = "\"8\"";
        EvidenceWriteRequest writeRequest = new EvidenceWriteRequest();
        EvidenceSupersedeRequest supersedeRequest = new EvidenceSupersedeRequest();
        EvidenceEntry body = new EvidenceEntry();
        ResponseEntity<EvidenceEntry> downstream = new ResponseEntity<>(
                body, unsafeDownstreamHeaders("\"9\""), HttpStatus.ACCEPTED);

        when(evidenceLibraryApi.getEvidenceWithHttpInfo(entryId)).thenReturn(downstream);
        when(evidenceLibraryApi.createEvidenceWithHttpInfo(writeRequest)).thenReturn(downstream);
        when(evidenceLibraryApi.updateEvidenceWithHttpInfo(
                entryId, writeRequest, ifMatch)).thenReturn(downstream);
        when(evidenceLibraryApi.confirmEvidenceWithHttpInfo(
                entryId, ifMatch)).thenReturn(downstream);
        when(evidenceLibraryApi.hideEvidenceWithHttpInfo(
                entryId, ifMatch)).thenReturn(downstream);
        when(evidenceLibraryApi.showEvidenceWithHttpInfo(
                entryId, ifMatch)).thenReturn(downstream);
        when(evidenceLibraryApi.restoreEvidenceWithHttpInfo(
                entryId, ifMatch)).thenReturn(downstream);
        when(evidenceLibraryApi.supersedeEvidenceWithHttpInfo(
                entryId, supersedeRequest, ifMatch)).thenReturn(downstream);

        List<ResponseEntity<EvidenceEntry>> responses = List.of(
                userManagementService.getEvidence("token", entryId),
                userManagementService.createEvidence("token", writeRequest),
                userManagementService.updateEvidence(
                        "token", entryId, ifMatch, writeRequest),
                userManagementService.confirmEvidence("token", entryId, ifMatch),
                userManagementService.hideEvidence("token", entryId, ifMatch),
                userManagementService.showEvidence("token", entryId, ifMatch),
                userManagementService.restoreEvidence("token", entryId, ifMatch),
                userManagementService.supersedeEvidence(
                        "token", entryId, ifMatch, supersedeRequest));

        responses.forEach(response -> {
            assertNotSame(downstream, response);
            assertEquals(HttpStatus.ACCEPTED, response.getStatusCode());
            assertSame(body, response.getBody());
            assertEquals("\"9\"", response.getHeaders().getETag());
            assertUnsafeDownstreamHeadersWereDropped(response.getHeaders());
        });
    }

    @Test
    void getEvidence_WithoutEtagCopiesNoDownstreamHeaders() {
        UUID entryId = UUID.randomUUID();
        HttpHeaders downstreamHeaders = unsafeDownstreamHeaders(null);
        ResponseEntity<EvidenceEntry> downstream = new ResponseEntity<>(
                new EvidenceEntry(), downstreamHeaders, HttpStatus.OK);
        when(evidenceLibraryApi.getEvidenceWithHttpInfo(entryId)).thenReturn(downstream);

        ResponseEntity<EvidenceEntry> response =
                userManagementService.getEvidence("token", entryId);

        assertTrue(response.getHeaders().isEmpty());
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
        WorkPreferences preferences = new WorkPreferences();
        preferences.setAvailableFrom(LocalDate.of(2026, 10, 1));
        UserProfile profile = new UserProfile(
            List.of("Java"),
            List.of(new Qualification()),
            List.of(new Role()),
            new Aspirations(),
            preferences
        );

        when(authenticationApi.login(any())).thenReturn(loginResponse);
        when(authenticationApi.getCurrentUser("Bearer jwt-token")).thenReturn(accountResponse);
        when(userProfilesApi.getMyProfile()).thenReturn(downstreamProfile(profile));

        GatewayResponse response = userManagementService.login(request);

        assertTrue(response.isSuccess(), response.getMessage());
        assertEquals(200, response.getStatusCode());
        assertNotNull(response.getUser());
        assertEquals(LocalDate.of(2026, 10, 1),
                response.getUser().getProfile().getWorkPreferences().getAvailableFrom());
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
        when(userProfilesApi.createOrUpdateMyProfile(any(), isNull()))
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
        when(userProfilesApi.getMyProfile()).thenReturn(downstreamProfile(profile));

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
        when(userProfilesApi.createOrUpdateMyProfile(any(), isNull()))
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
        return new ObjectMapper().findAndRegisterModules().convertValue(
                profile,
                com.jobseekercopilot.generated.userprofileservice.model.UserProfile.class);
    }

    private HttpHeaders unsafeDownstreamHeaders(String etag) {
        HttpHeaders headers = new HttpHeaders();
        if (etag != null) {
            headers.setETag(etag);
        }
        headers.setContentLength(5_887);
        headers.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
        headers.set(HttpHeaders.CONNECTION, "keep-alive, X-Remove-Me");
        headers.add("X-Correlation-ID", "downstream-one");
        headers.add("X-Correlation-ID", "downstream-two");
        headers.add(HttpHeaders.SET_COOKIE, "downstream-session=must-not-cross-gateway");
        headers.set("X-Remove-Me", "named by Connection");
        headers.set("X-Downstream-Only", "must-not-cross-gateway");
        return headers;
    }

    private void assertUnsafeDownstreamHeadersWereDropped(HttpHeaders headers) {
        assertEquals(1, headers.size());
        assertFalse(headers.containsKey(HttpHeaders.CONTENT_LENGTH));
        assertFalse(headers.containsKey(HttpHeaders.TRANSFER_ENCODING));
        assertFalse(headers.containsKey(HttpHeaders.CONNECTION));
        assertFalse(headers.containsKey("X-Correlation-ID"));
        assertFalse(headers.containsKey(HttpHeaders.SET_COOKIE));
        assertFalse(headers.containsKey("X-Remove-Me"));
        assertFalse(headers.containsKey("X-Downstream-Only"));
    }
}

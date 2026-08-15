package com.jobseekercopilot.usermanagementgateway.controller;

import com.jobseekercopilot.generated.userprofileservice.model.EvidenceEntry;
import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.model.LoginRequest;
import com.jobseekercopilot.usermanagementgateway.model.RegisterRequest;
import com.jobseekercopilot.usermanagementgateway.model.SessionOutcome;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry;
import com.jobseekercopilot.usermanagementgateway.security.RefreshCoordinator;
import com.jobseekercopilot.usermanagementgateway.security.SessionCookieService;
import com.jobseekercopilot.usermanagementgateway.security.AuthenticationRateLimitFilter;
import com.jobseekercopilot.usermanagementgateway.service.UserManagementService;
import com.jobseekercopilot.usermanagementgateway.web.RequestBodySizeFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.hasItems;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
@TestPropertySource(properties = "gateway.request.maximum-body-bytes=512")
class ControllerValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private UserManagementService userManagementService;

    @MockBean
    private GatewayTelemetry telemetry;

    @MockBean
    private SessionCookieService sessionCookieService;

    @MockBean
    private RefreshCoordinator refreshCoordinator;

    @MockBean
    private AuthenticationRateLimitFilter authenticationRateLimitFilter;

    @MockBean
    private CsrfTokenRepository csrfTokens;

    @Test
    void rejectsShortPasswordWithVersionedFieldError() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Example User","email":"user@example.test","password":"too-short",
                                 "termsAccepted":true,"privacyNoticeAcknowledged":true,
                                 "ageEligibilityConfirmed":true,"legalVersion":"2026-08-15"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.schemaVersion").value("1"))
                .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.violations[0].field").value("password"))
                .andExpect(jsonPath("$.error.violations[0].code").value("UNICODELENGTH"));

        verifyNoInteractions(userManagementService);
    }

    @Test
    void acceptsFifteenUnicodeCodePointsAndNormalizesIdentityWhitespace() throws Exception {
        when(userManagementService.registerSession(any())).thenReturn(SessionOutcome.failure(
                new GatewayResponse(201, true, "Registered")));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" Example User ","email":" user@example.test ","password":"🌱🌱🌱🌱🌱🌱🌱🌱🌱🌱🌱🌱🌱🌱🌱",
                                 "termsAccepted":true,"privacyNoticeAcknowledged":true,
                                 "ageEligibilityConfirmed":true,"legalVersion":"2026-08-15"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterRequest> request = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(userManagementService).registerSession(request.capture());
        assertEquals("Example User", request.getValue().getName());
        assertEquals("user@example.test", request.getValue().getEmail());
        assertTrue(request.getValue().isTermsAccepted());
        assertTrue(request.getValue().isPrivacyNoticeAcknowledged());
        assertTrue(request.getValue().isAgeEligibilityConfirmed());
        assertEquals("2026-08-15", request.getValue().getLegalVersion());
    }

    @Test
    void rejectsRegistrationUnlessEveryLegalAcknowledgementIsExplicit() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Example User","email":"user@example.test",
                                 "password":"A valid local passphrase 2026!",
                                 "termsAccepted":false,"privacyNoticeAcknowledged":true,
                                 "ageEligibilityConfirmed":true,"legalVersion":"2026-08-15"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.violations[0].field").value("termsAccepted"));

        verifyNoInteractions(userManagementService);
    }

    @Test
    void rejectsPasswordAboveUnicodeMaximum() throws Exception {
        String password = "x".repeat(129);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Example User","email":"user@example.test","password":"%s",
                                 "termsAccepted":true,"privacyNoticeAcknowledged":true,
                                 "ageEligibilityConfirmed":true,"legalVersion":"2026-08-15"}
                                """.formatted(password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.violations[0].field").value("password"));

        verifyNoInteractions(userManagementService);
    }

    @Test
    void rejectsEvidenceLongFormTextAboveThePinnedProducerMaximum() throws Exception {
        String request = objectMapper.writeValueAsString(java.util.Map.of(
                "category", "EMPLOYMENT",
                "heading", "Example role",
                "description", "x".repeat(2001)));

        mockMvc.perform(post("/api/auth/evidence")
                        .principal(bearerAuthentication("access-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.violations[0].field").value("description"))
                .andExpect(jsonPath("$.error.violations[0].code").value("SIZE"));

        verifyNoInteractions(userManagementService);
    }

    @Test
    void acceptsEvidenceLongFormTextAtThePinnedProducerMaximum() throws Exception {
        when(userManagementService.createEvidence(any(), any()))
                .thenReturn(ResponseEntity.ok(new EvidenceEntry()));
        String request = objectMapper.writeValueAsString(java.util.Map.of(
                "category", "EMPLOYMENT",
                "heading", "Example role",
                "description", "x".repeat(2000)));

        mockMvc.perform(post("/api/auth/evidence")
                        .principal(bearerAuthentication("access-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isOk());

        verify(userManagementService).createEvidence(any(), any());
    }

    @Test
    void rejectsUnsafeProfessionalContactBeforeCallingDownstream() throws Exception {
        mockMvc.perform(patch("/api/auth/profile/professional-contact")
                        .principal(bearerAuthentication("access-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "phone": "+44 7700 900123",
                                  "links": [
                                    {"label": "Portfolio", "url": "http://example.test/portfolio"}
                                  ]
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.violations[0].field").value("links[0].url"))
                .andExpect(jsonPath("$.error.violations[0].code").value("HTTPSURL"));

        verifyNoInteractions(userManagementService);
    }

    @Test
    void rejectsOwnershipFieldsFromProfessionalContactRequest() throws Exception {
        mockMvc.perform(patch("/api/auth/profile/professional-contact")
                        .principal(bearerAuthentication("access-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"userId":"other-user","phone":"+44 7700 900123","links":[]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_JSON"));

        verifyNoInteractions(userManagementService);
    }

    @Test
    void rejectsUnboundedProfessionalContactBeforeCallingDownstream() throws Exception {
        java.util.List<java.util.Map<String, String>> links =
                java.util.stream.IntStream.range(0, 9)
                        .mapToObj(index -> java.util.Map.of(
                                "label", "Link " + index,
                                "url", "https://example.test/profile/" + index))
                        .toList();
        String request = objectMapper.writeValueAsString(java.util.Map.of(
                "phone", "1".repeat(41),
                "links", links));

        mockMvc.perform(patch("/api/auth/profile/professional-contact")
                        .principal(bearerAuthentication("access-token"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(request))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.violations[*].field", hasItems("links", "phone")));

        verifyNoInteractions(userManagementService);
    }

    @Test
    void normalizesLoginEmailWithoutChangingPassword() throws Exception {
        when(userManagementService.loginSession(any())).thenReturn(SessionOutcome.failure(
                new GatewayResponse(200, true, "Logged in")));
        String password = "  A legacy passphrase  ";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":" user@example.test ","password":"%s"}
                                """.formatted(password)))
                .andExpect(status().isOk());

        ArgumentCaptor<LoginRequest> request = ArgumentCaptor.forClass(LoginRequest.class);
        verify(userManagementService).loginSession(request.capture());
        assertEquals("user@example.test", request.getValue().getEmail());
        assertEquals(password, request.getValue().getPassword());
    }

    @Test
    void rejectsMalformedJsonWithoutParserDetails() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is not valid JSON."))
                .andExpect(jsonPath("$.error.code").value("MALFORMED_JSON"));
    }

    @Test
    void rejectsUnknownRequestFieldsInsteadOfSilentlyDiscardingSchemaDrift() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.test","password":"A valid local passphrase 2026!","userId":"other-user"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("MALFORMED_JSON"));

        verifyNoInteractions(userManagementService);
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("credentials"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void rejectsDeclaredBodyAboveConfiguredLimit() throws Exception {
        String body = "x".repeat(513);

        MockMvc sizeLimitedMvc = MockMvcBuilders.webAppContextSetup(context)
                .addFilters(new RequestBodySizeFilter(512, objectMapper))
                .build();
        sizeLimitedMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.schemaVersion").value("1"))
                .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        when(userManagementService.loginSession(any()))
                .thenThrow(new IllegalStateException("sensitive implementation detail"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.test","password":"A valid local passphrase 2026!"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @Test
    void returnsSafeNotFoundForUnknownResource() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found."));
    }

    private BearerTokenAuthentication bearerAuthentication(String token) {
        var principal = new DefaultOAuth2AuthenticatedPrincipal(
                "user-123",
                java.util.Map.of("sub", "user-123"),
                java.util.List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                token,
                java.time.Instant.now(),
                java.time.Instant.now().plusSeconds(60));
        return new BearerTokenAuthentication(
                principal, accessToken, principal.getAuthorities());
    }
}

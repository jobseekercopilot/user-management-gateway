package com.jobseekercopilot.usermanagementgateway;

import com.jobseekercopilot.generated.authenticationservice.api.AuthenticationApi;
import com.jobseekercopilot.generated.userprofileservice.api.EvidenceLibraryApi;
import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import jakarta.servlet.http.Cookie;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
        "authentication.service.token=test-only-authentication-service-token-32-bytes",
        "gateway.security.auth-rate-maximum=100"
})
@AutoConfigureMockMvc
class GatewaySecurityIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationApi authenticationApi;

    @MockBean
    private UserProfilesApi userProfilesApi;

    @MockBean
    private EvidenceLibraryApi evidenceLibraryApi;

    @Test
    void csrfBootstrapIsPublicAndIssuesReadableSameSiteCookie() throws Exception {
        var result = mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-CSRF-Token"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andReturn();

        Cookie[] cookies = result.getResponse().getCookies();
        org.junit.jupiter.api.Assertions.assertEquals(1, cookies.length);
        org.junit.jupiter.api.Assertions.assertEquals("jsc-csrf-local", cookies[0].getName());
        org.junit.jupiter.api.Assertions.assertFalse(cookies[0].isHttpOnly());
        org.junit.jupiter.api.Assertions.assertFalse(cookies[0].getSecure());
        org.junit.jupiter.api.Assertions.assertEquals("/", cookies[0].getPath());
        org.junit.jupiter.api.Assertions.assertEquals("Lax", cookies[0].getAttribute("SameSite"));
    }

    @Test
    void everyStateChangingBrowserRouteRequiresCsrf() throws Exception {
        for (String path : List.of("/api/auth/register", "/api/auth/login",
                "/api/auth/refresh", "/api/auth/logout", "/api/auth/profile",
                "/api/auth/evidence",
                "/api/auth/evidence/00000000-0000-0000-0000-000000000001/confirm",
                "/api/auth/evidence/00000000-0000-0000-0000-000000000001/hide",
                "/api/auth/evidence/00000000-0000-0000-0000-000000000001/show",
                "/api/auth/evidence/00000000-0000-0000-0000-000000000001/archive",
                "/api/auth/evidence/00000000-0000-0000-0000-000000000001/restore",
                "/api/auth/evidence/00000000-0000-0000-0000-000000000001/supersede",
                "/api/auth/password-reset/request",
                "/api/auth/password-reset/complete")) {
            var request = path.endsWith("profile") ? put(path) : post(path);
            mockMvc.perform(request.contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden())
                    .andExpect(jsonPath("$.error.code").value("REQUEST_FORBIDDEN"));
        }
        for (String path : List.of(
                "/api/auth/profile",
                "/api/auth/evidence/00000000-0000-0000-0000-000000000001")) {
            mockMvc.perform(patch(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
            mockMvc.perform(put(path).contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isForbidden());
        }
        verifyNoInteractions(authenticationApi, userProfilesApi, evidenceLibraryApi);
    }

    @Test
    void passwordResetIsPublicWithCsrfAndCompletionClearsAllSessionCookies() throws Exception {
        mockMvc.perform(post("/api/auth/password-reset/request")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"user@example.test\"}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").value(
                        "If an account exists for that email, a password-reset link has been sent."));

        String token = "A".repeat(43);
        var completed = mockMvc.perform(post("/api/auth/password-reset/complete")
                        .with(csrf().asHeader())
                        .cookie(new Cookie("jsc-access-local", "old-access"),
                                new Cookie("jsc-refresh-local", "old-refresh"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"%s","newPassword":"A secure replacement passphrase 2026!"}
                                """.formatted(token)))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString(token))))
                .andReturn();

        assertSetCookies(completed.getResponse().getHeaders(HttpHeaders.SET_COOKIE),
                "jsc-access-local=;", "jsc-refresh-local=;");
        verify(authenticationApi).requestPasswordReset(any());
        verify(authenticationApi).completePasswordReset(any());
    }

    @Test
    void loginSetsHttpOnlyCookiesAndNeverReturnsTokens() throws Exception {
        when(authenticationApi.login(any())).thenReturn(login("access-secret", "refresh-secret"));
        when(authenticationApi.getCurrentUser("Bearer access-secret")).thenReturn(account());
        when(userProfilesApi.getMyProfile("Bearer access-secret")).thenReturn(profile());

        var result = mockMvc.perform(post("/api/auth/login")
                        .with(csrf().asHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.test","password":"A valid local passphrase 2026!"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("user-123"))
                .andExpect(jsonPath("$.user.token").doesNotExist())
                .andExpect(content().string(not(containsString("access-secret"))))
                .andExpect(content().string(not(containsString("refresh-secret"))))
                .andReturn();

        List<String> cookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
        org.junit.jupiter.api.Assertions.assertEquals(2, cookies.size());
        org.junit.jupiter.api.Assertions.assertTrue(cookies.stream().allMatch(value ->
                value.contains("HttpOnly") && value.contains("SameSite=Lax") && !value.contains("Secure")));
        org.junit.jupiter.api.Assertions.assertTrue(cookies.stream().anyMatch(value ->
                value.startsWith("jsc-access-local=") && value.contains("Path=/")));
        org.junit.jupiter.api.Assertions.assertTrue(cookies.stream().anyMatch(value ->
                value.startsWith("jsc-refresh-local=") && value.contains("Path=/")));
    }

    @Test
    void protectedProfileUsesOnlyCookieAndForwardsEndUserBearerToken() throws Exception {
        when(authenticationApi.getCurrentUser("Bearer valid-access")).thenReturn(account());
        when(userProfilesApi.getMyProfile("Bearer valid-access")).thenReturn(profile());

        mockMvc.perform(get("/api/auth/profile")
                        .cookie(new Cookie("jsc-access-local", "valid-access"))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer browser-forgery")
                        .header("X-User-Id", "victim"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.id").value("user-123"));

        verify(userProfilesApi).getMyProfile("Bearer valid-access");
        verify(userProfilesApi, never()).getMyProfile(contains("browser-forgery"));
        verify(userProfilesApi, never()).getMyProfile(contains("victim"));
    }

    @Test
    void browserAuthorizationHeaderAloneHasNoAuthorityAndInvalidCookieIsRedacted() throws Exception {
        mockMvc.perform(get("/api/auth/profile")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer browser-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REQUIRED"))
                .andExpect(content().string(not(containsString("browser-token"))));

        when(authenticationApi.getCurrentUser("Bearer invalid-cookie"))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED, "decoder detail"));
        mockMvc.perform(get("/api/auth/profile")
                        .cookie(new Cookie("jsc-access-local", "invalid-cookie")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REQUIRED"))
                .andExpect(content().string(not(containsString("invalid-cookie"))))
                .andExpect(content().string(not(containsString("decoder"))));
    }

    @Test
    void authenticationDependencyFailureIsRecoverableAndNotReportedAsAnInvalidUserSession() throws Exception {
        when(authenticationApi.getCurrentUser("Bearer outage-cookie"))
                .thenThrow(new ResourceAccessException("private connection detail"));

        mockMvc.perform(get("/api/auth/profile")
                        .cookie(new Cookie("jsc-access-local", "outage-cookie")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.error.code").value("DEPENDENCY_UNAVAILABLE"))
                .andExpect(content().string(not(containsString("outage-cookie"))))
                .andExpect(content().string(not(containsString("private connection"))));
    }

    @Test
    void unlistedRouteIsDeniedByDefaultWithoutRevealingItsImplementation() throws Exception {
        mockMvc.perform(get("/internal/accidental-route"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_REQUIRED"))
                .andExpect(content().string(not(containsString("accidental-route"))));
    }

    @Test
    void refreshRotatesCookiesAndFailureClearsThem() throws Exception {
        when(authenticationApi.refresh(any())).thenReturn(login("new-access", "new-refresh"));
        var rotated = mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf().asHeader())
                        .cookie(new Cookie("jsc-refresh-local", "old-refresh")))
                .andExpect(status().isOk())
                .andExpect(content().string(not(containsString("new-access"))))
                .andReturn();
        assertSetCookies(rotated.getResponse().getHeaders(HttpHeaders.SET_COOKIE),
                "jsc-access-local=new-access", "jsc-refresh-local=new-refresh");

        when(authenticationApi.refresh(any()))
                .thenThrow(new HttpClientErrorException(HttpStatus.UNAUTHORIZED));
        var cleared = mockMvc.perform(post("/api/auth/refresh")
                        .with(csrf().asHeader())
                        .cookie(new Cookie("jsc-refresh-local", "different-old-refresh")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("SESSION_EXPIRED"))
                .andReturn();
        assertSetCookies(cleared.getResponse().getHeaders(HttpHeaders.SET_COOKIE),
                "jsc-access-local=;", "jsc-refresh-local=;");
    }

    @Test
    void logoutRevokesDownstreamSessionAndClearsCookies() throws Exception {
        var loggedOut = mockMvc.perform(post("/api/auth/logout")
                        .with(csrf().asHeader())
                        .cookie(new Cookie("jsc-access-local", "logout-access"),
                                new Cookie("jsc-refresh-local", "logout-refresh")))
                .andExpect(status().isOk())
                .andReturn();
        assertSetCookies(loggedOut.getResponse().getHeaders(HttpHeaders.SET_COOKIE),
                "jsc-access-local=;", "jsc-refresh-local=;");
        verify(authenticationApi).logout("Bearer logout-access");
    }

    @Test
    void corsIsCredentialedOnlyForConfiguredExactOriginAndSecurityHeadersArePresent() throws Exception {
        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "http://localhost:4200")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:4200"))
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));

        mockMvc.perform(options("/api/auth/login")
                        .header(HttpHeaders.ORIGIN, "https://attacker.example")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST"))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("Content-Security-Policy", containsString("default-src 'none'")));
    }

    private com.jobseekercopilot.generated.authenticationservice.model.LoginResponse login(
            String access, String refresh) {
        return new com.jobseekercopilot.generated.authenticationservice.model.LoginResponse()
                .token(access).refreshToken(refresh).tokenType("Bearer").expiresIn(900L);
    }

    private void assertSetCookies(List<String> cookies, String... fragments) {
        org.junit.jupiter.api.Assertions.assertEquals(fragments.length, cookies.size());
        for (String fragment : fragments) {
            org.junit.jupiter.api.Assertions.assertTrue(
                    cookies.stream().anyMatch(cookie -> cookie.contains(fragment)), fragment);
        }
    }

    private com.jobseekercopilot.generated.authenticationservice.model.UserAccountResponse account() {
        return new com.jobseekercopilot.generated.authenticationservice.model.UserAccountResponse()
                .id("user-123").name("Example User").email("user@example.test");
    }

    private com.jobseekercopilot.generated.userprofileservice.model.UserProfile profile() {
        return new com.jobseekercopilot.generated.userprofileservice.model.UserProfile()
                .skills(List.of("Testing"));
    }
}

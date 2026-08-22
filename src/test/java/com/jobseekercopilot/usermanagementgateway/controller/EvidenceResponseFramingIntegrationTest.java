package com.jobseekercopilot.usermanagementgateway.controller;

import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import com.jobseekercopilot.generated.authenticationservice.api.AuthenticationApi;
import com.jobseekercopilot.generated.authenticationservice.api.AccountLifecycleApi;
import com.jobseekercopilot.generated.userprofileservice.api.EvidenceLibraryApi;
import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceEntry;
import com.jobseekercopilot.usermanagementgateway.config.UserProfileAccessTokenContext;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry;
import com.jobseekercopilot.usermanagementgateway.security.AuthenticationRateLimitFilter;
import com.jobseekercopilot.usermanagementgateway.security.RefreshCoordinator;
import com.jobseekercopilot.usermanagementgateway.security.SessionCookieService;
import com.jobseekercopilot.usermanagementgateway.service.UserManagementService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(UserManagementController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import({UserManagementService.class, UserProfileAccessTokenContext.class})
class EvidenceResponseFramingIntegrationTest {

    private static final long WRONG_DOWNSTREAM_CONTENT_LENGTH = 5_887;

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthenticationApi authenticationApi;

    @MockBean
    private AccountLifecycleApi accountLifecycleApi;

    @MockBean
    private UserProfilesApi userProfilesApi;

    @MockBean
    private EvidenceLibraryApi evidenceLibraryApi;

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
    void evidenceListDoesNotForwardDownstreamFramingHeadersToHttpCaller() throws Exception {
        HttpHeaders downstreamHeaders = new HttpHeaders();
        downstreamHeaders.setETag("\"12\"");
        downstreamHeaders.setContentLength(WRONG_DOWNSTREAM_CONTENT_LENGTH);
        downstreamHeaders.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
        downstreamHeaders.set(HttpHeaders.CONNECTION, "keep-alive, X-Remove-Me");
        downstreamHeaders.add("X-Correlation-ID", "downstream-one");
        downstreamHeaders.add("X-Correlation-ID", "downstream-two");
        downstreamHeaders.add(
                HttpHeaders.SET_COOKIE, "downstream-session=must-not-cross-gateway");
        downstreamHeaders.set("X-Remove-Me", "named by Connection");
        ResponseEntity<List<EvidenceEntry>> downstream = new ResponseEntity<>(
                List.of(new EvidenceEntry()), downstreamHeaders, HttpStatus.OK);
        when(evidenceLibraryApi.listEvidenceWithHttpInfo(false)).thenReturn(downstream);

        var result = mockMvc.perform(get("/api/auth/evidence")
                        .principal(authentication("access-token")))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ETAG, "\"12\""))
                .andExpect(header().doesNotExist(HttpHeaders.TRANSFER_ENCODING))
                .andExpect(header().doesNotExist(HttpHeaders.CONNECTION))
                .andExpect(header().doesNotExist("X-Correlation-ID"))
                .andExpect(header().doesNotExist(HttpHeaders.SET_COOKIE))
                .andExpect(header().doesNotExist("X-Remove-Me"))
                .andExpect(jsonPath("$").isArray())
                .andReturn();

        assertNotEquals(
                Long.toString(WRONG_DOWNSTREAM_CONTENT_LENGTH),
                result.getResponse().getHeader(HttpHeaders.CONTENT_LENGTH));
    }

    private BearerTokenAuthentication authentication(String token) {
        var principal = new DefaultOAuth2AuthenticatedPrincipal(
                "user-123",
                java.util.Map.of("sub", "user-123"),
                List.of(new SimpleGrantedAuthority("ROLE_USER")));
        var accessToken = new OAuth2AccessToken(
                OAuth2AccessToken.TokenType.BEARER,
                token,
                Instant.now(),
                Instant.now().plusSeconds(60));
        return new BearerTokenAuthentication(
                principal, accessToken, principal.getAuthorities());
    }
}

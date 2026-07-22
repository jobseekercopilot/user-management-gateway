package com.jobseekercopilot.usermanagementgateway.security;

import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.model.SessionOutcome;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.*;

class SessionCookieServiceTest {

    @Test
    void productionHostCookiesAreSecureHttpOnlyHostScopedAndClearedWithMatchingPath() {
        GatewaySecurityProperties properties = GatewaySecurityPropertiesTest.valid(true);
        SessionCookieService cookies = new SessionCookieService(properties);

        ResponseEntity<GatewayResponse> established = cookies.withSession(
                ResponseEntity.ok(), new GatewayResponse(200, true, "ok"),
                new SessionOutcome(new GatewayResponse(200, true, "ok"),
                        "access-value", "refresh-value", 900));
        assertHostCookies(established.getHeaders().get(HttpHeaders.SET_COOKIE), false);

        ResponseEntity<GatewayResponse> cleared = cookies.clearSession(
                ResponseEntity.ok(), new GatewayResponse(200, true, "ok"));
        assertHostCookies(cleared.getHeaders().get(HttpHeaders.SET_COOKIE), true);
    }

    @Test
    void sessionOutcomeStringNeverRendersCredentialValues() {
        SessionOutcome outcome = new SessionOutcome(new GatewayResponse(200, true, "ok"),
                "access-secret", "refresh-secret", 900);
        assertEquals("SessionOutcome[authenticated=true]", outcome.toString());
        assertFalse(outcome.toString().contains("secret"));
    }

    private void assertHostCookies(List<String> values, boolean cleared) {
        assertNotNull(values);
        assertEquals(2, values.size());
        assertTrue(values.stream().anyMatch(value -> value.startsWith("__Host-jsc-access=")));
        assertTrue(values.stream().anyMatch(value -> value.startsWith("__Host-jsc-refresh=")));
        assertTrue(values.stream().allMatch(value -> value.contains("Path=/")
                && value.contains("Secure") && value.contains("HttpOnly")
                && value.contains("SameSite=Lax") && !value.contains("Domain=")));
        if (cleared) {
            assertTrue(values.stream().allMatch(value -> value.contains("Max-Age=0")));
        }
    }
}

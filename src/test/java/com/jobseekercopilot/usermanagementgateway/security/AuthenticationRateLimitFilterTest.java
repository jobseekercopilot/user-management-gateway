package com.jobseekercopilot.usermanagementgateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.*;

class AuthenticationRateLimitFilterTest {

    @Test
    void limitsByDirectPeerWithBoundedStableResponseAndIgnoresForwardedAddress() throws Exception {
        GatewaySecurityProperties properties = GatewaySecurityPropertiesTest.valid(false);
        properties.getSecurity().setAuthRateMaximum(2);
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                properties, new ObjectMapper(),
                Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC));

        for (int attempt = 0; attempt < 2; attempt++) {
            MockHttpServletRequest request = request("203.0.113.10");
            MockHttpServletResponse response = new MockHttpServletResponse();
            filter.doFilter(request, response, new MockFilterChain());
            assertNotEquals(429, response.getStatus());
        }

        MockHttpServletRequest blocked = request("203.0.113.10");
        blocked.addHeader("X-Forwarded-For", "198.51.100.200");
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(blocked, response, new MockFilterChain());

        assertEquals(429, response.getStatus());
        assertEquals("60", response.getHeader("Retry-After"));
        assertTrue(response.getContentAsString().contains("TOO_MANY_AUTHENTICATION_ATTEMPTS"));
        assertFalse(response.getContentAsString().contains("203.0.113.10"));
        assertFalse(response.getContentAsString().contains("198.51.100.200"));
    }

    private MockHttpServletRequest request(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(address);
        return request;
    }
}

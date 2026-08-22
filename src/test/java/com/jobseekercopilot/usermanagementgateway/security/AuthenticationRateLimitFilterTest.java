package com.jobseekercopilot.usermanagementgateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
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

    @Test
    void retryAfterTracksControlledElapsedTimeAndResetsAtTheWindowBoundary() throws Exception {
        GatewaySecurityProperties properties = GatewaySecurityPropertiesTest.valid(false);
        properties.getSecurity().setAuthRateMaximum(1);
        properties.getSecurity().setAuthRateWindowSeconds(60);
        MutableClock clock = new MutableClock(Instant.parse("2026-07-22T00:00:00Z"));
        AuthenticationRateLimitFilter filter = new AuthenticationRateLimitFilter(
                properties, new ObjectMapper(), clock);

        MockHttpServletResponse initialAllowed = execute(filter, "203.0.113.10");
        assertNotEquals(429, initialAllowed.getStatus());
        assertEquals("60", execute(filter, "203.0.113.10").getHeader("Retry-After"));

        clock.advanceSeconds(59);
        assertEquals("1", execute(filter, "203.0.113.10").getHeader("Retry-After"));

        clock.advanceSeconds(1);
        MockHttpServletResponse nextWindowAllowed = execute(filter, "203.0.113.10");
        assertNotEquals(429, nextWindowAllowed.getStatus());
        assertEquals("60", execute(filter, "203.0.113.10").getHeader("Retry-After"));
    }

    private MockHttpServletResponse execute(
            AuthenticationRateLimitFilter filter, String address) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request(address), response, new MockFilterChain());
        return response;
    }

    private MockHttpServletRequest request(String address) {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setRemoteAddr(address);
        return request;
    }

    private static final class MutableClock extends Clock {
        private Instant instant;
        private final ZoneId zone;

        private MutableClock(Instant instant) {
            this(instant, ZoneOffset.UTC);
        }

        private MutableClock(Instant instant, ZoneId zone) {
            this.instant = instant;
            this.zone = zone;
        }

        private void advanceSeconds(long seconds) {
            instant = instant.plusSeconds(seconds);
        }

        @Override
        public ZoneId getZone() {
            return zone;
        }

        @Override
        public Clock withZone(ZoneId newZone) {
            return new MutableClock(instant, newZone);
        }

        @Override
        public Instant instant() {
            return instant;
        }
    }
}

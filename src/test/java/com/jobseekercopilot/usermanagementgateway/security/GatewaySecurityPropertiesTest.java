package com.jobseekercopilot.usermanagementgateway.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GatewaySecurityPropertiesTest {

    @Test
    void acceptsExplicitLocalAndSecureProductionBoundaries() {
        GatewaySecurityProperties local = valid(false);
        assertDoesNotThrow(local::validate);

        GatewaySecurityProperties production = valid(true);
        assertDoesNotThrow(production::validate);
    }

    @Test
    void rejectsWildcardUnsafeOriginCookieAndBoundConfigurationWithoutEchoingValues() {
        for (String origin : java.util.List.of("*", "https://*.example.test",
                "https://user:password@example.test", "https://example.test/path",
                "file:///tmp/browser")) {
            GatewaySecurityProperties properties = valid(true);
            properties.getSecurity().setAllowedOrigins(origin);
            IllegalStateException failure = assertThrows(IllegalStateException.class, properties::validate);
            assertEquals("Gateway browser security configuration is invalid", failure.getMessage());
            assertFalse(failure.getMessage().contains(origin));
        }

        GatewaySecurityProperties insecureProductionName = valid(false);
        insecureProductionName.getSession().setAccessCookieName("__Host-jsc-access");
        assertThrows(IllegalStateException.class, insecureProductionName::validate);

        GatewaySecurityProperties unprefixedSecureCookie = valid(true);
        unprefixedSecureCookie.getSession().setRefreshCookieName("refresh");
        assertThrows(IllegalStateException.class, unprefixedSecureCookie::validate);
    }

    static GatewaySecurityProperties valid(boolean secure) {
        GatewaySecurityProperties properties = new GatewaySecurityProperties();
        var session = properties.getSession();
        session.setAccessCookieName(secure ? "__Host-jsc-access" : "jsc-access-local");
        session.setRefreshCookieName(secure ? "__Host-jsc-refresh" : "jsc-refresh-local");
        session.setCsrfCookieName(secure ? "__Host-jsc-csrf" : "jsc-csrf-local");
        session.setSecureCookies(secure);
        session.setAccessMaximumSeconds(900);
        session.setRefreshMaximumSeconds(604800);
        session.setRefreshConcurrencySeconds(5);
        session.setRefreshConcurrencyMaximum(1000);
        var security = properties.getSecurity();
        security.setAllowedOrigins(secure ? "https://app.example.test" : "http://localhost:4200");
        security.setAuthRateMaximum(20);
        security.setAuthRateGlobalMaximum(1000);
        security.setAuthRateWindowSeconds(60);
        security.setAuthRateMaximumClients(10000);
        return properties;
    }
}

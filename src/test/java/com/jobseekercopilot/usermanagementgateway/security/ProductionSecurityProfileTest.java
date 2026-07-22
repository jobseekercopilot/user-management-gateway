package com.jobseekercopilot.usermanagementgateway.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {
        "authentication.service.token=test-only-authentication-service-token-32-bytes",
        "GATEWAY_ALLOWED_ORIGINS=https://app.example.test"
})
@ActiveProfiles("production")
class ProductionSecurityProfileTest {

    @Autowired
    private GatewaySecurityProperties properties;

    @Autowired
    private Environment environment;

    @Test
    void productionProfileForcesSecureHostCookiesExactHttpsOriginAndDisablesDocs() {
        assertTrue(properties.getSession().isSecureCookies());
        assertEquals("__Host-jsc-access", properties.getSession().getAccessCookieName());
        assertEquals("__Host-jsc-refresh", properties.getSession().getRefreshCookieName());
        assertEquals("__Host-jsc-csrf", properties.getSession().getCsrfCookieName());
        assertEquals(java.util.List.of("https://app.example.test"), properties.getSecurity().origins());
        assertEquals("false", environment.getProperty("springdoc.api-docs.enabled"));
        assertEquals("false", environment.getProperty("springdoc.swagger-ui.enabled"));
        assertEquals("health,info", environment.getProperty("management.endpoints.web.exposure.include"));
    }
}

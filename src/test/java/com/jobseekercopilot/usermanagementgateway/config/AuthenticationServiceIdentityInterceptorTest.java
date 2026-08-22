package com.jobseekercopilot.usermanagementgateway.config;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.net.URI;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;

class AuthenticationServiceIdentityInterceptorTest {

    @Test
    void injectsConfiguredIdentityAndReplacesAnyCallerSuppliedValue() throws Exception {
        String configured = "test-only-authentication-service-token-32-bytes";
        var interceptor = new AuthenticationServiceIdentityInterceptor(configured);
        var request = new MockClientHttpRequest(HttpMethod.POST, URI.create("http://auth/api/auth/login"));
        request.getHeaders().set(AuthenticationServiceIdentityInterceptor.HEADER_NAME, "untrusted-browser-value");

        interceptor.intercept(request, new byte[0], (forwarded, body) -> {
            assertEquals(configured,
                    forwarded.getHeaders().getFirst(AuthenticationServiceIdentityInterceptor.HEADER_NAME));
            return new MockClientHttpResponse(new byte[0], 200);
        });
    }

    @Test
    void rejectsMissingOrShortIdentityWithoutReflectingIt() {
        IllegalStateException blank = assertThrows(IllegalStateException.class,
                () -> new AuthenticationServiceIdentityInterceptor(""));
        IllegalStateException shortToken = assertThrows(IllegalStateException.class,
                () -> new AuthenticationServiceIdentityInterceptor("short-secret"));

        assertEquals("Authentication service identity token must contain at least 32 bytes.", blank.getMessage());
        assertEquals(blank.getMessage(), shortToken.getMessage());
    }
}

package com.jobseekercopilot.usermanagementgateway.config;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;

@Component
public class AuthenticationServiceIdentityInterceptor implements ClientHttpRequestInterceptor {

    static final String HEADER_NAME = "X-Service-Token";
    static final int MINIMUM_TOKEN_BYTES = 32;

    private final String serviceToken;

    public AuthenticationServiceIdentityInterceptor(
            @Value("${authentication.service.token}") String serviceToken) {
        if (serviceToken == null
                || serviceToken.isBlank()
                || serviceToken.getBytes(StandardCharsets.UTF_8).length < MINIMUM_TOKEN_BYTES) {
            throw new IllegalStateException(
                    "Authentication service identity token must contain at least 32 bytes.");
        }
        this.serviceToken = serviceToken;
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request, byte[] body, ClientHttpRequestExecution execution) throws IOException {
        request.getHeaders().set(HEADER_NAME, serviceToken);
        return execution.execute(request, body);
    }
}

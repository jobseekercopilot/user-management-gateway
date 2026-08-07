package com.jobseekercopilot.usermanagementgateway.config;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;

class UserProfileClientTest {

    @Test
    void generatedUserProfileApiUsesConfiguredBasePath() {
        ClientHttpRequestInterceptor correlation = (request, body, execution) ->
                execution.execute(request, body);
        var resilience = new DownstreamResilienceInterceptor("user-profile-service", 2, 3, 30_000, 32);
        var accessTokenContext = new UserProfileAccessTokenContext();
        var api = new DownstreamApiConfig().userProfilesApi(
                "http://localhost:8085", correlation, resilience,
                accessTokenContext, 500, 2_000);

        assertEquals("http://localhost:8085", api.getApiClient().getBasePath());
    }

    @Test
    void generatedBearerSecurityUsesTheScopedClaimantToken() throws IOException {
        AtomicReference<String> authorization = new AtomicReference<>();
        HttpServer downstream = HttpServer.create(new InetSocketAddress(0), 0);
        downstream.createContext("/api/profiles/me", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] response = "{\"skills\":[],\"qualifications\":[],\"roles\":[]}"
                    .getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });
        downstream.start();
        try {
            ClientHttpRequestInterceptor correlation = (request, body, execution) ->
                    execution.execute(request, body);
            var resilience =
                    new DownstreamResilienceInterceptor("user-profile-service", 1, 3, 30_000, 1);
            var accessTokenContext = new UserProfileAccessTokenContext();
            var api = new DownstreamApiConfig().userProfilesApi(
                    "http://127.0.0.1:" + downstream.getAddress().getPort(),
                    correlation, resilience, accessTokenContext, 500, 2_000);

            accessTokenContext.withToken("claimant-access-token", api::getMyProfile);

            assertEquals("Bearer claimant-access-token", authorization.get());
        } finally {
            downstream.stop(0);
        }
    }
}

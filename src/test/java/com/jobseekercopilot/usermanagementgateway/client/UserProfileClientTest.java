package com.jobseekercopilot.usermanagementgateway.config;

import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestInterceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void generatedProfessionalContactOperationUsesOwnerPathBearerAndIfMatch() throws IOException {
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> authorization = new AtomicReference<>();
        AtomicReference<String> ifMatch = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        HttpServer downstream = HttpServer.create(new InetSocketAddress(0), 0);
        downstream.createContext("/api/profiles/me/professional-contact", exchange -> {
            method.set(exchange.getRequestMethod());
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            ifMatch.set(exchange.getRequestHeaders().getFirst("If-Match"));
            requestBody.set(new String(
                    exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] response = """
                    {
                      "revision": 4,
                      "professionalContact": {
                        "phone": "+44 7700 900123",
                        "links": [
                          {"label":"Portfolio","url":"https://example.test/portfolio"}
                        ]
                      }
                    }
                    """.getBytes(StandardCharsets.UTF_8);
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
            var contact = new com.jobseekercopilot.generated.userprofileservice.model.ProfessionalContact()
                    .phone("+44 7700 900123")
                    .links(List.of(
                            new com.jobseekercopilot.generated.userprofileservice.model.ProfessionalLink()
                                    .label("Portfolio")
                                    .url("https://example.test/portfolio")));

            var response = accessTokenContext.withToken(
                    "claimant-access-token",
                    () -> api.updateMyProfessionalContact(contact, "\"3\""));

            assertEquals("PATCH", method.get());
            assertEquals("Bearer claimant-access-token", authorization.get());
            assertEquals("\"3\"", ifMatch.get());
            assertFalse(requestBody.get().contains("userId"));
            assertEquals(4L, response.getRevision());
            assertEquals("https://example.test/portfolio",
                    response.getProfessionalContact().getLinks().get(0).getUrl());
        } finally {
            downstream.stop(0);
        }
    }
}

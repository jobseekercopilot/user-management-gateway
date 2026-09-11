package com.jobseekercopilot.usermanagementgateway.config;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DownstreamApiConfigTest {

    @Test
    void configuresInterceptorsForDownstreamRequests() {
        ClientHttpRequestInterceptor correlation = (request, body, execution) ->
                execution.execute(request, body);
        var resilience = new DownstreamResilienceInterceptor("test-service", 2, 3, 30_000, 32);

        var restTemplate = new DownstreamApiConfig().restTemplate(
                correlation, resilience, 321, 654);

        assertEquals(2, restTemplate.getInterceptors().size());
    }

    @Test
    void rejectsNonPositiveTimeoutConfiguration() {
        ClientHttpRequestInterceptor correlation = (request, body, execution) ->
                execution.execute(request, body);
        var resilience = new DownstreamResilienceInterceptor("test-service", 2, 3, 30_000, 32);
        var config = new DownstreamApiConfig();

        assertThrows(IllegalArgumentException.class,
                () -> config.restTemplate(correlation, resilience, 0, 500));
        assertThrows(IllegalArgumentException.class,
                () -> config.restTemplate(correlation, resilience, 500, 0));
    }

    @Test
    void readDeadlineStopsASlowDownstream() throws IOException {
        HttpServer slowServer = HttpServer.create(new InetSocketAddress(0), 0);
        slowServer.createContext("/slow", exchange -> {
            try {
                Thread.sleep(250);
                exchange.sendResponseHeaders(200, 0);
                exchange.getResponseBody().close();
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
            } finally {
                exchange.close();
            }
        });
        slowServer.start();
        try {
            ClientHttpRequestInterceptor correlation = (request, body, execution) ->
                    execution.execute(request, body);
            var resilience = new DownstreamResilienceInterceptor("slow-service", 1, 3, 30_000, 1);
            var restTemplate = new DownstreamApiConfig().restTemplate(
                    correlation, resilience, 500, 50);

            assertThrows(ResourceAccessException.class,
                    () -> restTemplate.getForEntity(
                            "http://127.0.0.1:" + slowServer.getAddress().getPort() + "/slow",
                            String.class));
        } finally {
            slowServer.stop(0);
        }
    }

    @Test
    void patchRequestsReachTheDownstreamService() throws IOException {
        AtomicReference<String> receivedMethod = new AtomicReference<>();
        AtomicReference<String> receivedBody = new AtomicReference<>();
        HttpServer downstream = HttpServer.create(new InetSocketAddress(0), 0);
        downstream.createContext("/profile", exchange -> {
            receivedMethod.set(exchange.getRequestMethod());
            receivedBody.set(new String(
                    exchange.getRequestBody().readAllBytes(),
                    StandardCharsets.UTF_8));
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
        });
        downstream.start();
        try {
            ClientHttpRequestInterceptor correlation = (request, body, execution) ->
                    execution.execute(request, body);
            var resilience = new DownstreamResilienceInterceptor("patch-service", 1, 3, 30_000, 1);
            // Generous connect/read timeouts: this test verifies PATCH method and
            // body pass through to the downstream, not timeout behaviour. Tight
            // 500ms values flake against the loopback server under heavy parallel
            // CI load; timeout enforcement is covered by the slow-service test.
            var restTemplate = new DownstreamApiConfig().restTemplate(
                    correlation, resilience, 5_000, 5_000);

            restTemplate.exchange(
                    "http://127.0.0.1:" + downstream.getAddress().getPort() + "/profile",
                    HttpMethod.PATCH,
                    new HttpEntity<>("{\"targetRoles\":[\"Developer\"]}"),
                    Void.class);

            assertEquals("PATCH", receivedMethod.get());
            assertEquals("{\"targetRoles\":[\"Developer\"]}", receivedBody.get());
        } finally {
            downstream.stop(0);
        }
    }
}

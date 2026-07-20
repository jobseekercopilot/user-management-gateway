package com.jobseekercopilot.usermanagementgateway.config;

import java.io.IOException;
import java.net.InetSocketAddress;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DownstreamApiConfigTest {

    @Test
    void configuresBoundedConnectAndReadTimeoutsForSlowDownstreams() {
        ClientHttpRequestInterceptor correlation = (request, body, execution) ->
                execution.execute(request, body);
        var resilience = new DownstreamResilienceInterceptor("test-service", 2, 3, 30_000, 32);

        var restTemplate = new DownstreamApiConfig().restTemplate(
                correlation, resilience, 321, 654);
        Object requestFactory = ReflectionTestUtils.getField(
                restTemplate.getRequestFactory(), "requestFactory");

        assertEquals(321, ReflectionTestUtils.getField(
                requestFactory, "connectTimeout"));
        assertEquals(654, ReflectionTestUtils.getField(
                requestFactory, "readTimeout"));
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
}

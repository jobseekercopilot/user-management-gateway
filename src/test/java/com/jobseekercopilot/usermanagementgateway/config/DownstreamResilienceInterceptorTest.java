package com.jobseekercopilot.usermanagementgateway.config;

import java.io.IOException;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.Status;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DownstreamResilienceInterceptorTest {

    private final MockClientHttpRequest getRequest =
            new MockClientHttpRequest(HttpMethod.GET, URI.create("http://downstream.test/resource"));
    private final MockClientHttpRequest postRequest =
            new MockClientHttpRequest(HttpMethod.POST, URI.create("http://downstream.test/resource"));

    @Test
    void retriesOneSafeGetAfterServerFailure() throws IOException {
        DownstreamResilienceInterceptor interceptor = interceptor(2, 3, 1);
        int[] calls = {0};
        ClientHttpRequestExecution execution = (request, body) -> {
            calls[0]++;
            return new MockClientHttpResponse(
                    new byte[0], calls[0] == 1 ? HttpStatus.SERVICE_UNAVAILABLE : HttpStatus.OK);
        };

        ClientHttpResponse response = interceptor.intercept(getRequest, new byte[0], execution);

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(2, calls[0]);
        assertFalse(interceptor.isOpen());
    }

    @Test
    void doesNotRetryUnsafePost() {
        DownstreamResilienceInterceptor interceptor = interceptor(3, 3, 1);
        int[] calls = {0};
        ClientHttpRequestExecution execution = (request, body) -> {
            calls[0]++;
            throw new IOException("unavailable");
        };

        assertThrows(IOException.class,
                () -> interceptor.intercept(postRequest, new byte[0], execution));
        assertEquals(1, calls[0]);
    }

    @Test
    void opensCircuitAfterFailureThresholdAndRejectsWithoutCallingDownstream() {
        DownstreamResilienceInterceptor interceptor = interceptor(1, 2, 1);
        int[] calls = {0};
        ClientHttpRequestExecution execution = (request, body) -> {
            calls[0]++;
            throw new IOException("unavailable");
        };

        assertThrows(IOException.class,
                () -> interceptor.intercept(getRequest, new byte[0], execution));
        assertThrows(IOException.class,
                () -> interceptor.intercept(getRequest, new byte[0], execution));
        assertTrue(interceptor.isOpen());
        assertThrows(ResourceAccessException.class,
                () -> interceptor.intercept(getRequest, new byte[0], execution));
        assertEquals(2, calls[0]);
    }

    @Test
    void rejectsConcurrentCallWhenBulkheadIsFull() throws Exception {
        DownstreamResilienceInterceptor interceptor = interceptor(1, 3, 1);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        ClientHttpRequestExecution blockingExecution = (request, body) -> {
            entered.countDown();
            try {
                if (!release.await(2, TimeUnit.SECONDS)) {
                    throw new IOException("test release timed out");
                }
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new IOException("interrupted", interrupted);
            }
            return new MockClientHttpResponse(new byte[0], HttpStatus.OK);
        };

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            var firstCall = executor.submit(
                    () -> interceptor.intercept(getRequest, new byte[0], blockingExecution));
            assertTrue(entered.await(1, TimeUnit.SECONDS));
            assertThrows(ResourceAccessException.class,
                    () -> interceptor.intercept(getRequest, new byte[0], blockingExecution));
            release.countDown();
            assertEquals(HttpStatus.OK, firstCall.get(1, TimeUnit.SECONDS).getStatusCode());
            assertEquals(1, interceptor.availablePermits());
        } finally {
            release.countDown();
            executor.shutdownNow();
        }
    }

    @Test
    void healthIsDownWhenOneObservedDependencyCircuitIsOpen() {
        DownstreamResilienceInterceptor authentication = interceptor(1, 1, 1);
        DownstreamResilienceInterceptor profile =
                new DownstreamResilienceInterceptor("user-profile-service", 1, 1, 30_000, 1);
        ClientHttpRequestExecution failure = (request, body) -> {
            throw new IOException("unavailable");
        };
        assertThrows(IOException.class,
                () -> authentication.intercept(getRequest, new byte[0], failure));

        var health = new DownstreamDependenciesHealthIndicator(authentication, profile).health();

        assertEquals(Status.DOWN, health.getStatus());
        assertEquals("OPEN", health.getDetails().get("test-service"));
        assertEquals("AVAILABLE", health.getDetails().get("user-profile-service"));
    }

    @Test
    void rejectsNonPositiveResilienceConfiguration() {
        assertThrows(IllegalArgumentException.class,
                () -> new DownstreamResilienceInterceptor("test-service", 0, 1, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DownstreamResilienceInterceptor("test-service", 1, 0, 1, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DownstreamResilienceInterceptor("test-service", 1, 1, 0, 1));
        assertThrows(IllegalArgumentException.class,
                () -> new DownstreamResilienceInterceptor("test-service", 1, 1, 1, 0));
    }

    private DownstreamResilienceInterceptor interceptor(
            int maxAttempts, int failureThreshold, int maxConcurrent) {
        return new DownstreamResilienceInterceptor(
                "test-service", maxAttempts, failureThreshold, 30_000, maxConcurrent);
    }
}

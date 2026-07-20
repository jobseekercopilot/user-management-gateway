package com.jobseekercopilot.usermanagementgateway.config;

import java.io.IOException;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.http.HttpMethod;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.ResourceAccessException;

public final class DownstreamResilienceInterceptor implements ClientHttpRequestInterceptor {

    private final String dependencyName;
    private final int maxAttempts;
    private final int failureThreshold;
    private final long openDurationNanos;
    private final Semaphore bulkhead;
    private final AtomicInteger consecutiveFailures = new AtomicInteger();
    private final AtomicLong openUntilNanos = new AtomicLong();

    public DownstreamResilienceInterceptor(
            String dependencyName,
            int maxAttempts,
            int failureThreshold,
            long openDurationMs,
            int maxConcurrent) {
        if (maxAttempts < 1 || failureThreshold < 1 || openDurationMs < 1 || maxConcurrent < 1) {
            throw new IllegalArgumentException("Downstream resilience values must all be positive");
        }
        this.dependencyName = dependencyName;
        this.maxAttempts = maxAttempts;
        this.failureThreshold = failureThreshold;
        this.openDurationNanos = openDurationMs * 1_000_000;
        this.bulkhead = new Semaphore(maxConcurrent);
    }

    @Override
    public ClientHttpResponse intercept(
            HttpRequest request,
            byte[] body,
            ClientHttpRequestExecution execution) throws IOException {
        rejectIfCircuitOpen();
        if (!bulkhead.tryAcquire()) {
            throw new ResourceAccessException(dependencyName + " bulkhead is full");
        }

        try {
            int attempts = request.getMethod() == HttpMethod.GET ? maxAttempts : 1;
            IOException lastFailure = null;
            for (int attempt = 1; attempt <= attempts; attempt++) {
                try {
                    ClientHttpResponse response = execution.execute(request, body);
                    if (response.getStatusCode().is5xxServerError()) {
                        recordFailure();
                        if (attempt < attempts) {
                            response.close();
                            continue;
                        }
                    } else {
                        recordSuccess();
                    }
                    return response;
                } catch (IOException failure) {
                    recordFailure();
                    lastFailure = failure;
                    if (attempt == attempts) {
                        throw failure;
                    }
                }
            }
            throw lastFailure;
        } finally {
            bulkhead.release();
        }
    }

    public boolean isOpen() {
        return System.nanoTime() < openUntilNanos.get();
    }

    public int availablePermits() {
        return bulkhead.availablePermits();
    }

    public String dependencyName() {
        return dependencyName;
    }

    private void rejectIfCircuitOpen() {
        if (isOpen()) {
            throw new ResourceAccessException(dependencyName + " circuit is open");
        }
        if (openUntilNanos.get() != 0) {
            openUntilNanos.set(0);
            consecutiveFailures.set(0);
        }
    }

    private void recordSuccess() {
        consecutiveFailures.set(0);
        openUntilNanos.set(0);
    }

    private void recordFailure() {
        if (consecutiveFailures.incrementAndGet() >= failureThreshold) {
            openUntilNanos.set(System.nanoTime() + openDurationNanos);
        }
    }
}

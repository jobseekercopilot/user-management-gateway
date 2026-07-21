package com.jobseekercopilot.usermanagementgateway.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry.UserOperation;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.util.Set;
import org.junit.jupiter.api.Test;

class GatewayTelemetryTest {

    @Test
    void recordsBoundedOperationAuthenticationAndLatencyMetrics() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new GatewayTelemetry(registry);

        telemetry.record(UserOperation.LOGIN, new GatewayResponse(200, true, "user@example.test"), 10_000_000);
        telemetry.record(UserOperation.REGISTER, new GatewayResponse(503, false, "Bearer sensitive-token"), 20_000_000);

        assertEquals(1.0, registry.get("jobseeker.user.management.operation.outcomes")
                .tags("operation", "login", "outcome", "success", "status", "2xx")
                .counter().count());
        assertEquals(1.0, registry.get("jobseeker.user.management.authentication.outcomes")
                .tags("operation", "register", "outcome", "dependency_unavailable", "status", "5xx")
                .counter().count());
        assertEquals(1, registry.get("jobseeker.user.management.operation.duration")
                .tags("operation", "register", "outcome", "dependency_unavailable", "status", "5xx")
                .timer().count());

        Set<String> allowedTags = Set.of("operation", "outcome", "status", "le");
        for (Meter meter : registry.getMeters()) {
            meter.getId().getTags().forEach(tag -> {
                assertFalse(tag.getValue().contains("user@example.test"));
                assertFalse(tag.getValue().contains("sensitive-token"));
                assertFalse(tag.getValue().startsWith("Bearer "));
                assertFalse(!allowedTags.contains(tag.getKey()), "unexpected tag: " + tag.getKey());
            });
        }
    }

    @Test
    void mapsFailuresToAStableLowCardinalityOutcomeSet() {
        var registry = new SimpleMeterRegistry();
        var telemetry = new GatewayTelemetry(registry);

        telemetry.record(UserOperation.PROFILE_READ, new GatewayResponse(401, false, "invalid"), 1);
        telemetry.record(UserOperation.PROFILE_UPDATE, new GatewayResponse(429, false, "limited"), 1);
        telemetry.record(UserOperation.PROFILE_READ, new GatewayResponse(500, false, "failed"), 1);

        assertEquals(1.0, operationCounter(registry, "profile_read", "authentication_failed", "4xx"));
        assertEquals(1.0, operationCounter(registry, "profile_update", "rate_limited", "4xx"));
        assertEquals(1.0, operationCounter(registry, "profile_read", "internal_error", "5xx"));
    }

    private double operationCounter(SimpleMeterRegistry registry, String operation, String outcome, String status) {
        return registry.get("jobseeker.user.management.operation.outcomes")
                .tags("operation", operation, "outcome", outcome, "status", status)
                .counter().count();
    }
}

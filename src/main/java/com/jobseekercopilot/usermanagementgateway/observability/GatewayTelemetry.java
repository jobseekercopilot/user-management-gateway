package com.jobseekercopilot.usermanagementgateway.observability;

import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.stereotype.Component;

@Component
public class GatewayTelemetry {

    public enum UserOperation {
        REGISTER("register", true),
        LOGIN("login", true),
        PROFILE_READ("profile_read", false),
        PROFILE_UPDATE("profile_update", false);

        private final String tag;
        private final boolean authentication;

        UserOperation(String tag, boolean authentication) {
            this.tag = tag;
            this.authentication = authentication;
        }
    }

    private final MeterRegistry registry;

    public GatewayTelemetry(MeterRegistry registry) {
        this.registry = registry;
    }

    public void record(UserOperation operation, GatewayResponse response, long elapsedNanos) {
        String outcome = outcome(response);
        String statusFamily = statusFamily(response.getStatusCode());

        Counter.builder("jobseeker.user.management.operation.outcomes")
                .description("Completed user-management operations by bounded outcome")
                .tags("operation", operation.tag, "outcome", outcome, "status", statusFamily)
                .register(registry)
                .increment();

        Timer.builder("jobseeker.user.management.operation.duration")
                .description("User-management operation latency")
                .tags("operation", operation.tag, "outcome", outcome, "status", statusFamily)
                .publishPercentileHistogram()
                .serviceLevelObjectives(
                        Duration.ofMillis(100), Duration.ofMillis(500), Duration.ofSeconds(2))
                .register(registry)
                .record(Math.max(0, elapsedNanos), TimeUnit.NANOSECONDS);

        if (operation.authentication) {
            Counter.builder("jobseeker.user.management.authentication.outcomes")
                    .description("Registration and login outcomes without identity labels")
                    .tags("operation", operation.tag, "outcome", outcome, "status", statusFamily)
                    .register(registry)
                    .increment();
        }
    }

    private String outcome(GatewayResponse response) {
        if (response.isSuccess()) {
            return "success";
        }
        return switch (response.getStatusCode()) {
            case 400, 413, 415 -> "invalid_request";
            case 401, 403 -> "authentication_failed";
            case 404 -> "not_found";
            case 409 -> "conflict";
            case 429 -> "rate_limited";
            case 502, 503, 504 -> "dependency_unavailable";
            default -> response.getStatusCode() >= 500 ? "internal_error" : "rejected";
        };
    }

    private String statusFamily(int status) {
        if (status >= 200 && status < 300) {
            return "2xx";
        }
        if (status >= 400 && status < 500) {
            return "4xx";
        }
        if (status >= 500 && status < 600) {
            return "5xx";
        }
        return "other";
    }
}

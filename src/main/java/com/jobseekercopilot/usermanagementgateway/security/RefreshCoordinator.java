package com.jobseekercopilot.usermanagementgateway.security;

import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.model.SessionOutcome;
import com.jobseekercopilot.usermanagementgateway.service.UserManagementService;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.util.HexFormat;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class RefreshCoordinator {

    private final UserManagementService service;
    private final GatewaySecurityProperties.Session properties;
    private final Clock clock;
    private final ConcurrentHashMap<String, Entry> outcomes = new ConcurrentHashMap<>();

    @Autowired
    public RefreshCoordinator(UserManagementService service, GatewaySecurityProperties properties) {
        this(service, properties, Clock.systemUTC());
    }

    RefreshCoordinator(
            UserManagementService service, GatewaySecurityProperties properties, Clock clock) {
        this.service = service;
        this.properties = properties.getSession();
        this.clock = clock;
    }

    public SessionOutcome refresh(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return service.refreshSession(refreshToken);
        }
        long now = clock.instant().getEpochSecond();
        outcomes.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        String digest = digest(refreshToken);

        while (true) {
            Entry existing = outcomes.get(digest);
            if (existing != null && existing.expiresAt > now) {
                return existing.result.join();
            }
            if (existing != null) {
                outcomes.remove(digest, existing);
                continue;
            }
            if (outcomes.size() >= properties.getRefreshConcurrencyMaximum()) {
                return SessionOutcome.failure(GatewayResponse.failure(
                        503, "SESSION_COORDINATION_UNAVAILABLE",
                        "The browser session could not be refreshed."));
            }
            Entry created = new Entry(now + properties.getRefreshConcurrencySeconds());
            if (outcomes.putIfAbsent(digest, created) == null) {
                try {
                    created.result.complete(service.refreshSession(refreshToken));
                } catch (RuntimeException exception) {
                    created.result.complete(SessionOutcome.failure(GatewayResponse.failure(
                            500, "INTERNAL_ERROR", "An unexpected error occurred.")));
                }
                return created.result.join();
            }
        }
    }

    private String digest(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable");
        }
    }

    private static final class Entry {
        private final long expiresAt;
        private final CompletableFuture<SessionOutcome> result = new CompletableFuture<>();

        private Entry(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}

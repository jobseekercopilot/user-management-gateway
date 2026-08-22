package com.jobseekercopilot.usermanagementgateway.security;

import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.model.SessionOutcome;
import com.jobseekercopilot.usermanagementgateway.service.UserManagementService;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class RefreshCoordinatorTest {

    @Test
    void concurrentReuseOfOneRefreshTokenIsCoalescedToOneRotation() throws Exception {
        UserManagementService service = mock(UserManagementService.class);
        GatewaySecurityProperties properties = GatewaySecurityPropertiesTest.valid(false);
        RefreshCoordinator coordinator = new RefreshCoordinator(
                service, properties,
                Clock.fixed(Instant.parse("2026-07-22T00:00:00Z"), ZoneOffset.UTC));
        SessionOutcome rotated = new SessionOutcome(
                new GatewayResponse(200, true, "refreshed"), "new-access", "new-refresh", 900);
        CountDownLatch entered = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        when(service.refreshSession("old-refresh")).thenAnswer(invocation -> {
            entered.countDown();
            assertTrue(release.await(5, TimeUnit.SECONDS));
            return rotated;
        });

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> coordinator.refresh("old-refresh"));
            assertTrue(entered.await(5, TimeUnit.SECONDS));
            var second = executor.submit(() -> coordinator.refresh("old-refresh"));
            release.countDown();

            assertSame(rotated, first.get(5, TimeUnit.SECONDS));
            assertSame(rotated, second.get(5, TimeUnit.SECONDS));
            verify(service, times(1)).refreshSession("old-refresh");
        } finally {
            executor.shutdownNow();
        }
    }
}

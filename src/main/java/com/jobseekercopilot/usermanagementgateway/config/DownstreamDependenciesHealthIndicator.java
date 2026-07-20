package com.jobseekercopilot.usermanagementgateway.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

@Component("downstreamDependencies")
public class DownstreamDependenciesHealthIndicator implements HealthIndicator {

    private final DownstreamResilienceInterceptor authentication;
    private final DownstreamResilienceInterceptor userProfile;

    public DownstreamDependenciesHealthIndicator(
            @Qualifier("authenticationResilienceInterceptor") DownstreamResilienceInterceptor authentication,
            @Qualifier("userProfileResilienceInterceptor") DownstreamResilienceInterceptor userProfile) {
        this.authentication = authentication;
        this.userProfile = userProfile;
    }

    @Override
    public Health health() {
        Health.Builder builder = authentication.isOpen() || userProfile.isOpen()
                ? Health.down()
                : Health.up();
        return builder
                .withDetail(authentication.dependencyName(), state(authentication))
                .withDetail(userProfile.dependencyName(), state(userProfile))
                .build();
    }

    private String state(DownstreamResilienceInterceptor interceptor) {
        return interceptor.isOpen() ? "OPEN" : "AVAILABLE";
    }
}

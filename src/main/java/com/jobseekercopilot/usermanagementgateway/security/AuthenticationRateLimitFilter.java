package com.jobseekercopilot.usermanagementgateway.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class AuthenticationRateLimitFilter extends OncePerRequestFilter {

    private static final Set<String> LIMITED_PATHS = Set.of(
            "/api/auth/register", "/api/auth/login", "/api/auth/refresh");

    private final GatewaySecurityProperties.Security properties;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final Map<String, Window> clients = new HashMap<>();
    private Window global;

    @Autowired
    public AuthenticationRateLimitFilter(
            GatewaySecurityProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, Clock.systemUTC());
    }

    AuthenticationRateLimitFilter(
            GatewaySecurityProperties properties, ObjectMapper objectMapper, Clock clock) {
        this.properties = properties.getSecurity();
        this.objectMapper = objectMapper;
        this.clock = clock;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !"POST".equals(request.getMethod()) || !LIMITED_PATHS.contains(request.getRequestURI());
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long retryAfter = consume(request.getRemoteAddr());
        if (retryAfter > 0) {
            response.setStatus(429);
            response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfter));
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), GatewayResponse.failure(
                    429, "TOO_MANY_AUTHENTICATION_ATTEMPTS",
                    "Too many authentication attempts. Try again later."));
            return;
        }
        chain.doFilter(request, response);
    }

    private synchronized long consume(String directPeer) {
        long now = clock.instant().getEpochSecond();
        long windowSeconds = properties.getAuthRateWindowSeconds();
        clients.entrySet().removeIf(entry -> entry.getValue().expiresAt <= now);
        if (global == null || global.expiresAt <= now) {
            global = new Window(now + windowSeconds);
        }
        if (global.count >= properties.getAuthRateGlobalMaximum()) {
            return Math.max(1, global.expiresAt - now);
        }
        Window client = clients.get(directPeer);
        if (client == null) {
            if (clients.size() >= properties.getAuthRateMaximumClients()) {
                return windowSeconds;
            }
            client = new Window(now + windowSeconds);
            clients.put(directPeer, client);
        }
        if (client.count >= properties.getAuthRateMaximum()) {
            return Math.max(1, client.expiresAt - now);
        }
        client.count++;
        global.count++;
        return 0;
    }

    private static final class Window {
        private final long expiresAt;
        private int count;

        private Window(long expiresAt) {
            this.expiresAt = expiresAt;
        }
    }
}

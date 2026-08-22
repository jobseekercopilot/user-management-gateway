package com.jobseekercopilot.usermanagementgateway.security;

import com.jobseekercopilot.usermanagementgateway.model.SessionOutcome;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import java.time.Duration;
import java.util.Arrays;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

@Component
public class SessionCookieService {

    private final GatewaySecurityProperties.Session properties;

    public SessionCookieService(GatewaySecurityProperties properties) {
        this.properties = properties.getSession();
    }

    public String accessToken(HttpServletRequest request) {
        return cookie(request, properties.getAccessCookieName());
    }

    public String refreshToken(HttpServletRequest request) {
        return cookie(request, properties.getRefreshCookieName());
    }

    public <T> ResponseEntity<T> withSession(ResponseEntity.BodyBuilder response, T body, SessionOutcome session) {
        long accessSeconds = Math.min(
                Math.max(1, session.accessExpiresInSeconds()), properties.getAccessMaximumSeconds());
        return response
                .header(HttpHeaders.SET_COOKIE, cookie(properties.getAccessCookieName(), session.accessToken(),
                        "/", true, Duration.ofSeconds(accessSeconds)).toString())
                .header(HttpHeaders.SET_COOKIE, cookie(properties.getRefreshCookieName(), session.refreshToken(),
                        "/", true, Duration.ofSeconds(properties.getRefreshMaximumSeconds())).toString())
                .body(body);
    }

    public <T> ResponseEntity<T> clearSession(ResponseEntity.BodyBuilder response, T body) {
        return response
                .header(HttpHeaders.SET_COOKIE, clear(properties.getAccessCookieName(), "/", true).toString())
                .header(HttpHeaders.SET_COOKIE, clear(properties.getRefreshCookieName(), "/", true).toString())
                .body(body);
    }

    private String cookie(HttpServletRequest request, String name) {
        if (request.getCookies() == null) {
            return null;
        }
        return Arrays.stream(request.getCookies())
                .filter(candidate -> name.equals(candidate.getName()))
                .map(Cookie::getValue)
                .filter(value -> value != null && !value.isBlank())
                .findFirst().orElse(null);
    }

    private ResponseCookie clear(String name, String path, boolean httpOnly) {
        return cookie(name, "", path, httpOnly, Duration.ZERO);
    }

    private ResponseCookie cookie(String name, String value, String path, boolean httpOnly, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(properties.isSecureCookies())
                .sameSite("Lax")
                .path(path)
                .maxAge(maxAge)
                .build();
    }
}

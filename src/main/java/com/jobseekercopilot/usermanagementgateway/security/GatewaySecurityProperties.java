package com.jobseekercopilot.usermanagementgateway.security;

import jakarta.annotation.PostConstruct;
import java.net.URI;
import java.util.Arrays;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "gateway")
public class GatewaySecurityProperties {

    private final Session session = new Session();
    private final Security security = new Security();

    public Session getSession() {
        return session;
    }

    public Security getSecurity() {
        return security;
    }

    @PostConstruct
    void validate() {
        if (session.accessMaximumSeconds < 1 || session.refreshMaximumSeconds < 1
                || session.refreshConcurrencySeconds < 1 || session.refreshConcurrencyMaximum < 1
                || security.authRateMaximum < 1 || security.authRateGlobalMaximum < 1
                || security.authRateGlobalMaximum < security.authRateMaximum || security.authRateWindowSeconds < 1
                || security.authRateMaximumClients < 1) {
            throw invalid();
        }
        if (!validCookieName(session.accessCookieName)
                || !validCookieName(session.refreshCookieName)
                || !validCookieName(session.csrfCookieName)) {
            throw invalid();
        }
        List<String> origins = security.origins();
        if (origins.isEmpty()) {
            throw invalid();
        }
        for (String origin : origins) {
            try {
                URI uri = URI.create(origin);
                boolean validScheme = "https".equals(uri.getScheme())
                        || (!session.secureCookies && "http".equals(uri.getScheme()));
                if (!uri.isAbsolute() || !validScheme || uri.getHost() == null
                        || uri.getUserInfo() != null || uri.getQuery() != null || uri.getFragment() != null
                        || (uri.getPath() != null && !uri.getPath().isEmpty()) || origin.contains("*")) {
                    throw invalid();
                }
            } catch (RuntimeException exception) {
                throw invalid();
            }
        }
        if (session.secureCookies
                && (!session.accessCookieName.startsWith("__Host-")
                || !session.refreshCookieName.startsWith("__Host-")
                || !session.csrfCookieName.startsWith("__Host-"))) {
            throw invalid();
        }
        if (!session.secureCookies
                && (session.accessCookieName.startsWith("__Host-")
                || session.refreshCookieName.startsWith("__Host-")
                || session.csrfCookieName.startsWith("__Host-"))) {
            throw invalid();
        }
    }

    private boolean validCookieName(String value) {
        return value != null && value.matches("[A-Za-z0-9_-]{1,64}");
    }

    private IllegalStateException invalid() {
        return new IllegalStateException("Gateway browser security configuration is invalid");
    }

    public static class Session {
        private String accessCookieName;
        private String refreshCookieName;
        private String csrfCookieName;
        private boolean secureCookies;
        private long accessMaximumSeconds;
        private long refreshMaximumSeconds;
        private long refreshConcurrencySeconds;
        private int refreshConcurrencyMaximum;

        public String getAccessCookieName() { return accessCookieName; }
        public void setAccessCookieName(String value) { accessCookieName = value; }
        public String getRefreshCookieName() { return refreshCookieName; }
        public void setRefreshCookieName(String value) { refreshCookieName = value; }
        public String getCsrfCookieName() { return csrfCookieName; }
        public void setCsrfCookieName(String value) { csrfCookieName = value; }
        public boolean isSecureCookies() { return secureCookies; }
        public void setSecureCookies(boolean value) { secureCookies = value; }
        public long getAccessMaximumSeconds() { return accessMaximumSeconds; }
        public void setAccessMaximumSeconds(long value) { accessMaximumSeconds = value; }
        public long getRefreshMaximumSeconds() { return refreshMaximumSeconds; }
        public void setRefreshMaximumSeconds(long value) { refreshMaximumSeconds = value; }
        public long getRefreshConcurrencySeconds() { return refreshConcurrencySeconds; }
        public void setRefreshConcurrencySeconds(long value) { refreshConcurrencySeconds = value; }
        public int getRefreshConcurrencyMaximum() { return refreshConcurrencyMaximum; }
        public void setRefreshConcurrencyMaximum(int value) { refreshConcurrencyMaximum = value; }
    }

    public static class Security {
        private String allowedOrigins;
        private int authRateMaximum;
        private int authRateGlobalMaximum;
        private long authRateWindowSeconds;
        private int authRateMaximumClients;

        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String value) { allowedOrigins = value; }
        public int getAuthRateMaximum() { return authRateMaximum; }
        public void setAuthRateMaximum(int value) { authRateMaximum = value; }
        public int getAuthRateGlobalMaximum() { return authRateGlobalMaximum; }
        public void setAuthRateGlobalMaximum(int value) { authRateGlobalMaximum = value; }
        public long getAuthRateWindowSeconds() { return authRateWindowSeconds; }
        public void setAuthRateWindowSeconds(long value) { authRateWindowSeconds = value; }
        public int getAuthRateMaximumClients() { return authRateMaximumClients; }
        public void setAuthRateMaximumClients(int value) { authRateMaximumClients = value; }

        public List<String> origins() {
            if (allowedOrigins == null) {
                return List.of();
            }
            return Arrays.stream(allowedOrigins.split(","))
                    .map(String::trim).filter(value -> !value.isEmpty()).distinct().toList();
        }
    }
}

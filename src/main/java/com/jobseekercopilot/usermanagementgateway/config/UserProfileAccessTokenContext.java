package com.jobseekercopilot.usermanagementgateway.config;

import java.util.function.Supplier;

/**
 * Supplies the current User Profile access token to the generated Bearer-auth client.
 *
 * <p>The generated API client is a singleton, so mutating its token for each request
 * would allow concurrent claimant requests to overwrite one another. This scoped
 * context keeps each token on the calling thread and always removes it after the
 * downstream invocation.</p>
 */
public class UserProfileAccessTokenContext {

    private static final String BEARER_PREFIX = "Bearer ";
    private final ThreadLocal<String> token = new ThreadLocal<>();

    public <T> T withToken(String accessToken, Supplier<T> invocation) {
        String previous = token.get();
        token.set(clean(accessToken));
        try {
            return invocation.get();
        } finally {
            if (previous == null) {
                token.remove();
            } else {
                token.set(previous);
            }
        }
    }

    String currentToken() {
        return token.get();
    }

    private String clean(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            throw new IllegalArgumentException("Access token must be supplied");
        }
        return accessToken.startsWith(BEARER_PREFIX)
                ? accessToken.substring(BEARER_PREFIX.length())
                : accessToken;
    }
}

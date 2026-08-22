package com.jobseekercopilot.usermanagementgateway.model;

public record SessionOutcome(
        GatewayResponse response,
        String accessToken,
        String refreshToken,
        long accessExpiresInSeconds) {

    public static SessionOutcome failure(GatewayResponse response) {
        return new SessionOutcome(response, null, null, 0);
    }

    public boolean authenticated() {
        return response != null && response.isSuccess()
                && accessToken != null && !accessToken.isBlank()
                && refreshToken != null && !refreshToken.isBlank()
                && accessExpiresInSeconds > 0;
    }

    @Override
    public String toString() {
        return "SessionOutcome[authenticated=" + authenticated() + "]";
    }
}

package com.jobseekercopilot.usermanagementgateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GatewayResponse {
    private int statusCode;
    private boolean success;
    private String message;
    private User user;
    private ApiError error;

    public boolean isSuccess() {
        return success;
    }

    public GatewayResponse(int statusCode, boolean success, String message) {
        this.statusCode = statusCode;
        this.success = success;
        this.message = message;
        if (!success) {
            this.error = new ApiError(defaultErrorCode(statusCode), message);
        }
    }

    public GatewayResponse(int statusCode, boolean success, String message, ApiError error) {
        this.statusCode = statusCode;
        this.success = success;
        this.message = message;
        this.error = error;
    }

    public GatewayResponse(int statusCode, boolean success, String message, User user) {
        this.statusCode = statusCode;
        this.success = success;
        this.message = message;
        this.user = user;
    }

    public static GatewayResponse failure(int statusCode, String code, String message) {
        return new GatewayResponse(statusCode, false, message, new ApiError(code, message));
    }

    public static GatewayResponse validationFailure(java.util.List<FieldViolation> violations) {
        String message = "Request validation failed.";
        return new GatewayResponse(400, false, message,
                new ApiError(ApiError.SCHEMA_VERSION, "REQUEST_VALIDATION_FAILED", message, violations));
    }

    private static String defaultErrorCode(int statusCode) {
        return switch (statusCode) {
            case 400 -> "BAD_REQUEST";
            case 401 -> "AUTHENTICATION_FAILED";
            case 404 -> "NOT_FOUND";
            case 409 -> "CONFLICT";
            case 413 -> "PAYLOAD_TOO_LARGE";
            case 415 -> "UNSUPPORTED_MEDIA_TYPE";
            case 429 -> "TOO_MANY_REQUESTS";
            case 503 -> "DEPENDENCY_UNAVAILABLE";
            default -> "INTERNAL_ERROR";
        };
    }
}

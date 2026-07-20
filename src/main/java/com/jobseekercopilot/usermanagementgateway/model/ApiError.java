package com.jobseekercopilot.usermanagementgateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        String schemaVersion,
        String code,
        String message,
        List<FieldViolation> violations) {

    public static final String SCHEMA_VERSION = "1";

    public ApiError(String code, String message) {
        this(SCHEMA_VERSION, code, message, List.of());
    }
}

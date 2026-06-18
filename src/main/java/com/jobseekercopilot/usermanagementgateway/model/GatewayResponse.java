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

    public GatewayResponse(int statusCode, boolean success, String message) {
        this.statusCode = statusCode;
        this.success = success;
        this.message = message;
    }
}

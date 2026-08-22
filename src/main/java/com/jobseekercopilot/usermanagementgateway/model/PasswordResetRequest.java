package com.jobseekercopilot.usermanagementgateway.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public class PasswordResetRequest {

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    public PasswordResetRequest() {
    }

    public PasswordResetRequest(String email) {
        this.email = email;
    }

    public String getEmail() {
        return email;
    }
}

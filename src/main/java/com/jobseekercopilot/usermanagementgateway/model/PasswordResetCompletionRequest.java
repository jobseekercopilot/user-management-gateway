package com.jobseekercopilot.usermanagementgateway.model;

import com.jobseekercopilot.usermanagementgateway.validation.UnicodeLength;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class PasswordResetCompletionRequest {

    @NotBlank
    @Size(max = 128)
    @Pattern(regexp = "^[A-Za-z0-9_-]{32,128}$")
    private String token;

    @NotBlank
    @UnicodeLength(min = 15, max = 128)
    private String newPassword;

    public PasswordResetCompletionRequest() {
    }

    public PasswordResetCompletionRequest(String token, String newPassword) {
        this.token = token;
        this.newPassword = newPassword;
    }

    public String getToken() {
        return token;
    }

    public String getNewPassword() {
        return newPassword;
    }
}

package com.jobseekercopilot.usermanagementgateway.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotBlank;
import com.jobseekercopilot.usermanagementgateway.validation.UnicodeLength;

@Getter
@NoArgsConstructor
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class LoginRequest {
    @NotBlank
    @Email
    @UnicodeLength(max = 254)
    @Schema(
            minLength = 1,
            maxLength = 254,
            format = "email",
            description = "Email identity after trimming, measured in Unicode code points.")
    private String email;
    @NotEmpty
    @UnicodeLength(max = 128)
    @Schema(
            minLength = 1,
            maxLength = 128,
            format = "password",
            description = "Current password measured in Unicode code points and forwarded exactly as supplied.")
    private String password;

    public void setEmail(String email) { this.email = email == null ? null : email.trim(); }
    public void setPassword(String password) { this.password = password; }
}

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
    private String email;
    @NotEmpty
    @UnicodeLength(max = 128)
    private String password;

    public void setEmail(String email) { this.email = email == null ? null : email.trim(); }
    public void setPassword(String password) { this.password = password; }
}

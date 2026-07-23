package com.jobseekercopilot.usermanagementgateway.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import com.jobseekercopilot.usermanagementgateway.validation.UnicodeLength;

@Getter
@NoArgsConstructor
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class RegisterRequest {
    @NotNull
    @UnicodeLength(min = 1, max = 100)
    @Schema(
            minLength = 1,
            maxLength = 100,
            description = "Display name after trimming, measured in Unicode code points.")
    private String name;
    @NotBlank
    @Email
    @UnicodeLength(max = 254)
    @Schema(
            minLength = 1,
            maxLength = 254,
            format = "email",
            description = "Email identity after trimming, measured in Unicode code points.")
    private String email;
    @NotNull
    @UnicodeLength(min = 15, max = 128)
    @Schema(
            minLength = 15,
            maxLength = 128,
            format = "password",
            description = "New password measured in Unicode code points and forwarded exactly as supplied.")
    private String password;
    @Valid
    private UserProfile profile;

    public void setName(String name) { this.name = name == null ? null : name.trim(); }
    public void setEmail(String email) { this.email = email == null ? null : email.trim(); }
    public void setPassword(String password) { this.password = password; }
    public void setProfile(UserProfile profile) { this.profile = profile; }
}

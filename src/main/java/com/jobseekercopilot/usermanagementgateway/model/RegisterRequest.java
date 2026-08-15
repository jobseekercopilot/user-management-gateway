package com.jobseekercopilot.usermanagementgateway.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
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
    @AssertTrue
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "True only after the current Terms of Use were actively accepted.")
    private boolean termsAccepted;
    @AssertTrue
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "True only after the current Privacy Notice was acknowledged.")
    private boolean privacyNoticeAcknowledged;
    @AssertTrue
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            description = "Confirms that the registrant is at least 18 years old.")
    private boolean ageEligibilityConfirmed;
    @NotBlank
    @Size(min = 1, max = 64)
    @Pattern(regexp = "[A-Za-z0-9][A-Za-z0-9._-]{0,63}")
    @Schema(
            requiredMode = Schema.RequiredMode.REQUIRED,
            minLength = 1,
            maxLength = 64,
            description = "Exact legal version returned by registration requirements.")
    private String legalVersion;
    @Valid
    private UserProfile profile;

    public void setName(String name) { this.name = name == null ? null : name.trim(); }
    public void setEmail(String email) { this.email = email == null ? null : email.trim(); }
    public void setPassword(String password) { this.password = password; }
    public void setTermsAccepted(boolean termsAccepted) { this.termsAccepted = termsAccepted; }
    public void setPrivacyNoticeAcknowledged(boolean privacyNoticeAcknowledged) {
        this.privacyNoticeAcknowledged = privacyNoticeAcknowledged;
    }
    public void setAgeEligibilityConfirmed(boolean ageEligibilityConfirmed) {
        this.ageEligibilityConfirmed = ageEligibilityConfirmed;
    }
    public void setLegalVersion(String legalVersion) { this.legalVersion = legalVersion; }
    public void setProfile(UserProfile profile) { this.profile = profile; }
}

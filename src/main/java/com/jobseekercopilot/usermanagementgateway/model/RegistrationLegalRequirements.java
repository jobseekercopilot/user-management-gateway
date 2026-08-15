package com.jobseekercopilot.usermanagementgateway.model;

import io.swagger.v3.oas.annotations.media.Schema;
import java.net.URI;
import java.util.regex.Pattern;

@Schema(
        description = "Server-authoritative legal requirements that must be displayed before registration.",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public record RegistrationLegalRequirements(
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minLength = 1, maxLength = 64)
        String legalVersion,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, minimum = "18", maximum = "18")
        int minimumAge,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uri", maxLength = 512)
        String termsUrl,
        @Schema(requiredMode = Schema.RequiredMode.REQUIRED, format = "uri", maxLength = 512)
        String privacyNoticeUrl) {

    private static final Pattern VERSION =
            Pattern.compile("[A-Za-z0-9][A-Za-z0-9._-]{0,63}");

    public RegistrationLegalRequirements {
        if (legalVersion == null || !VERSION.matcher(legalVersion).matches()) {
            throw new IllegalArgumentException("Invalid registration legal version");
        }
        if (minimumAge != 18) {
            throw new IllegalArgumentException("Unsupported registration minimum age");
        }
        termsUrl = requireReviewedUrl(termsUrl);
        privacyNoticeUrl = requireReviewedUrl(privacyNoticeUrl);
    }

    public static RegistrationLegalRequirements from(
            com.jobseekercopilot.generated.authenticationservice.model.RegistrationLegalRequirements
                    downstream) {
        if (downstream == null
                || downstream.getMinimumAge() == null) {
            throw new IllegalArgumentException("Registration requirements are unavailable");
        }
        return new RegistrationLegalRequirements(
                downstream.getLegalVersion(),
                downstream.getMinimumAge(),
                downstream.getTermsUrl() == null ? null : downstream.getTermsUrl().toASCIIString(),
                downstream.getPrivacyNoticeUrl() == null
                        ? null
                        : downstream.getPrivacyNoticeUrl().toASCIIString());
    }

    private static String requireReviewedUrl(String value) {
        try {
            if (value == null || value.length() > 512) {
                throw new IllegalArgumentException();
            }
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || uri.getHost() == null
                    || uri.getHost().isBlank()
                    || uri.getUserInfo() != null
                    || uri.getFragment() != null) {
                throw new IllegalArgumentException();
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid reviewed legal URL");
        }
    }
}

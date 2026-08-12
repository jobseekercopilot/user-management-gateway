package com.jobseekercopilot.usermanagementgateway.model;

import com.jobseekercopilot.usermanagementgateway.validation.HttpsUrl;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(
        description = "A user-declared labelled professional HTTPS link",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class ProfessionalLink {

    public static final int MAX_LABEL_LENGTH = 40;
    public static final int MAX_URL_LENGTH = 512;
    public static final String LABEL_PATTERN = "^[^\\p{Cc}]+$";

    @NotBlank
    @Size(min = 1, max = MAX_LABEL_LENGTH)
    @Pattern(regexp = LABEL_PATTERN)
    @Schema(
            example = "Portfolio",
            minLength = 1,
            maxLength = MAX_LABEL_LENGTH,
            pattern = LABEL_PATTERN)
    private String label;

    @NotBlank
    @Size(min = 9, max = MAX_URL_LENGTH)
    @HttpsUrl
    @Schema(
            example = "https://example.test/portfolio",
            minLength = 9,
            maxLength = MAX_URL_LENGTH,
            pattern = "^https://")
    private String url;
}

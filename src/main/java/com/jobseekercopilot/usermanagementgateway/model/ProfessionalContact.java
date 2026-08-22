package com.jobseekercopilot.usermanagementgateway.model;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Setter
@NoArgsConstructor
@Schema(
        description = "Private, user-declared contact details for owner-authorised document workflows",
        additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class ProfessionalContact {

    public static final int MAX_LINKS = 8;
    public static final int MAX_PHONE_LENGTH = 40;
    public static final String PHONE_PATTERN =
            "^\\s*$|^(?=(?:\\D*\\d){7,15}\\D*$)[+0-9() .-]+$";

    @Size(max = MAX_PHONE_LENGTH)
    @Pattern(regexp = PHONE_PATTERN)
    @Schema(
            description = "Optional user-declared professional telephone number; no value is inferred from documents",
            example = "+44 7700 900123",
            maxLength = MAX_PHONE_LENGTH,
            pattern = PHONE_PATTERN)
    private String phone;

    @Valid
    @Size(max = MAX_LINKS)
    private List<@NotNull @Valid ProfessionalLink> links = new ArrayList<>();
}

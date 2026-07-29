package com.jobseekercopilot.usermanagementgateway.model;

import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class UserProfile {
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long id;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String userId;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private Long revision;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private UUID revisionId;

    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    private String contentDigest;

    @Size(max = 100)
    private List<@Size(max = 100) String> skills;
    @Valid
    @Size(max = 50)
    private List<Qualification> qualifications;
    @Valid
    @Size(max = 50)
    private List<Role> roles;

    @Valid
    private Aspirations aspirations;
    @Valid
    private WorkPreferences workPreferences;

    public UserProfile(
            List<String> skills,
            List<Qualification> qualifications,
            List<Role> roles,
            Aspirations aspirations,
            WorkPreferences workPreferences) {
        this.skills = skills;
        this.qualifications = qualifications;
        this.roles = roles;
        this.aspirations = aspirations;
        this.workPreferences = workPreferences;
    }
}

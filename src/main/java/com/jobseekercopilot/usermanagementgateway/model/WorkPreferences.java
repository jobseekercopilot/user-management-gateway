package com.jobseekercopilot.usermanagementgateway.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class WorkPreferences {
    @Valid
    private PostcodeLocation location;
    @Min(0)
    @Max(500)
    private Integer commuteRange;
}

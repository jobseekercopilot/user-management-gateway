package com.jobseekercopilot.usermanagementgateway.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Size;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class Qualification {
    @Size(max = 200)
    private String qualificationName;
    @Size(max = 200)
    private String issuingBody;
    @Size(max = 50)
    private String status;
    @Size(max = 100)
    private String grade;
    @Size(max = 30)
    private String dateAchieved;
    @Size(max = 30)
    private String expectedCompletion;
}

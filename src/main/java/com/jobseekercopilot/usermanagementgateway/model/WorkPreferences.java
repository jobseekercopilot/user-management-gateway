package com.jobseekercopilot.usermanagementgateway.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.Set;
import com.jobseekercopilot.generated.userprofileservice.model.EmploymentType;
import com.jobseekercopilot.generated.userprofileservice.model.WorkingPattern;
import com.jobseekercopilot.generated.userprofileservice.model.WorkplaceArrangement;
import com.jobseekercopilot.generated.userprofileservice.model.CommuteTravelMode;

@Getter
@Setter
@NoArgsConstructor
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class WorkPreferences {
    @Valid
    private PostcodeLocation location;
    @Min(0)
    @Max(500)
    private Integer commuteRange;

    @Size(max = 2)
    private Set<CommuteTravelMode> commuteTravelModes;

    @Min(5)
    @Max(180)
    private Integer maximumDrivingMinutes;

    @Min(5)
    @Max(180)
    private Integer maximumTransitMinutes;

    @Size(max = 5)
    private Set<EmploymentType> employmentTypes;

    @Size(max = 8)
    private Set<WorkingPattern> workingPatterns;

    @Size(max = 3)
    private Set<WorkplaceArrangement> workplaceArrangements;

    private LocalDate availableFrom;

    @Min(0)
    @Max(3650)
    private Integer noticePeriodDays;

    public WorkPreferences(PostcodeLocation location, Integer commuteRange) {
        this.location = location;
        this.commuteRange = commuteRange;
    }
}

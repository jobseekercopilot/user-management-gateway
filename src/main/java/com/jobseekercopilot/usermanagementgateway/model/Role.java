package com.jobseekercopilot.usermanagementgateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Role {
    @Size(max = 200)
    private String jobTitle;
    @Size(max = 200)
    private String employer;
    @Size(max = 50)
    private String status;
    @Size(max = 30)
    private String startDate;
    @Size(max = 30)
    private String endDate;
    @Size(max = 2000)
    private String keyResponsibilities;
}

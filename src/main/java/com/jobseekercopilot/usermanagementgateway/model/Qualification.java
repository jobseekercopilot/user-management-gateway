package com.jobseekercopilot.usermanagementgateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;


@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Qualification {
    private String qualificationName;
    private String issuingBody;
    private String status;
    private String grade;
    private String dateAchieved;
    private String expectedCompletion;
}

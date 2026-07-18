package com.jobseekercopilot.usermanagementgateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkPreferences {
    private PostcodeLocation location;
    private Integer commuteRange;
}
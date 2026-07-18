package com.jobseekercopilot.usermanagementgateway.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
    private List<String> skills;
    private List<Qualification> qualifications;
    private List<Role> roles;

    private Aspirations aspirations;
    private WorkPreferences workPreferences;
}

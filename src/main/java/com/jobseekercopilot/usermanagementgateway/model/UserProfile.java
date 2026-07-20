package com.jobseekercopilot.usermanagementgateway.model;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {
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
}

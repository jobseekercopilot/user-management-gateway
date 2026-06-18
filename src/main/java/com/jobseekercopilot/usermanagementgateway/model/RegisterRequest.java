package com.jobseekercopilot.usermanagementgateway.model;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class RegisterRequest {
    private String name;
    private String email;
    private String password;
    private UserProfile profile;
}

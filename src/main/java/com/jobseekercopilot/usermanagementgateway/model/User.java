package com.jobseekercopilot.usermanagementgateway.model;

import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class User {
    private String id;
    private String name;
    private String email;
    private UserProfile profile;
    private String token;

    public User(String id, String name, String email, UserProfile profile) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.profile = profile;
    }
}
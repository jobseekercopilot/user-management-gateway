package com.jobseekercopilot.usermanagementgateway.model;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class PostcodeLocation {
    private String postcode;
    private String region;
    private String adminDistrict;
    private Double latitude;
    private Double longitude;
}
package com.jobseekercopilot.usermanagementgateway.model;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Schema(additionalProperties = Schema.AdditionalPropertiesValue.FALSE)
public class PostcodeLocation {
    @Size(max = 64)
    private String locationId;
    @Size(max = 200)
    private String displayName;
    @Size(max = 2)
    private String countryCode;
    @Size(max = 16)
    private String postcode;
    @Size(max = 100)
    private String region;
    @Size(max = 100)
    private String adminDistrict;
    @DecimalMin("-90.0")
    @DecimalMax("90.0")
    private Double latitude;
    @DecimalMin("-180.0")
    @DecimalMax("180.0")
    private Double longitude;
    private String locationType;
    private String precision;
    private String confidence;
    @Size(max = 256)
    private String googlePlaceId;
    @Size(max = 128)
    private String postcodesIoPlaceId;
    private String displayNameSource;
    private String postcodeSource;
    private String coordinatesSource;
}

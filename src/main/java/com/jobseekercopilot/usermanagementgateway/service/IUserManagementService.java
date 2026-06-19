package com.jobseekercopilot.usermanagementgateway.service;

import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.model.LoginRequest;
import com.jobseekercopilot.usermanagementgateway.model.RegisterRequest;
import com.jobseekercopilot.usermanagementgateway.model.UserProfile;

public interface IUserManagementService {
    GatewayResponse register(RegisterRequest request);
    GatewayResponse login(LoginRequest request);
    GatewayResponse getProfile(String token);
    GatewayResponse updateProfile(UserProfile profile, String token);
}

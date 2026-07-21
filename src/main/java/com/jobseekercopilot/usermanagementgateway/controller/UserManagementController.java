package com.jobseekercopilot.usermanagementgateway.controller;

import com.jobseekercopilot.usermanagementgateway.model.*;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry.UserOperation;
import com.jobseekercopilot.usermanagementgateway.service.UserManagementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
public class UserManagementController {

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GatewayTelemetry telemetry;

    @PostMapping(value = "/register", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Register a new user", description = "Registers a new user account. Coordinates with authentication-service to create the user.")
    @Tag(name = "Authentication")
    public ResponseEntity<GatewayResponse> register(@Valid @RequestBody RegisterRequest request) {
        long startedAt = System.nanoTime();
        GatewayResponse response = userManagementService.register(request);
        telemetry.record(UserOperation.REGISTER, response, System.nanoTime() - startedAt);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping(value = "/login", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Authenticate user", description = "Authenticates a user and returns a JWT token via authentication-service.")
    @Tag(name = "Authentication")
    public ResponseEntity<GatewayResponse> login(@Valid @RequestBody LoginRequest request) {
        long startedAt = System.nanoTime();
        GatewayResponse response = userManagementService.login(request);
        telemetry.record(UserOperation.LOGIN, response, System.nanoTime() - startedAt);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/profile")
    @Operation(summary = "Get user profile", description = "Retrieves the user profile. Requires JWT token in Authorization header.")
    @Tag(name = "Profile")
    public ResponseEntity<GatewayResponse> getProfile(
            @RequestParam(name = "email", required = false) String email,
            @RequestHeader(name = "Authorization", required = false) String token) {
        
        long startedAt = System.nanoTime();
        GatewayResponse response = userManagementService.getProfile(token);
        telemetry.record(UserOperation.PROFILE_READ, response, System.nanoTime() - startedAt);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping(value = "/profile", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Update user profile", description = "Updates the user profile. Requires JWT token in Authorization header.")
    @Tag(name = "Profile")
    public ResponseEntity<GatewayResponse> updateProfile(
            @RequestParam(name = "email", required = false) String email,
            @Valid @RequestBody UserProfile profile,
            @RequestHeader(name = "Authorization", required = false) String token) {
        
        long startedAt = System.nanoTime();
        GatewayResponse response = userManagementService.updateProfile(profile, token);
        telemetry.record(UserOperation.PROFILE_UPDATE, response, System.nanoTime() - startedAt);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}

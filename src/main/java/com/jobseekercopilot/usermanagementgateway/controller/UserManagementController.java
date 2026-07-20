package com.jobseekercopilot.usermanagementgateway.controller;

import com.jobseekercopilot.usermanagementgateway.model.*;
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

    @PostMapping(value = "/register", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Register a new user", description = "Registers a new user account. Coordinates with authentication-service to create the user.")
    @Tag(name = "Authentication")
    public ResponseEntity<GatewayResponse> register(@Valid @RequestBody RegisterRequest request) {
        GatewayResponse response = userManagementService.register(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping(value = "/login", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Authenticate user", description = "Authenticates a user and returns a JWT token via authentication-service.")
    @Tag(name = "Authentication")
    public ResponseEntity<GatewayResponse> login(@Valid @RequestBody LoginRequest request) {
        GatewayResponse response = userManagementService.login(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/profile")
    @Operation(summary = "Get user profile", description = "Retrieves the user profile. Requires JWT token in Authorization header.")
    @Tag(name = "Profile")
    public ResponseEntity<GatewayResponse> getProfile(
            @RequestParam(name = "email", required = false) String email,
            @RequestHeader(name = "Authorization", required = false) String token) {
        
        GatewayResponse response = userManagementService.getProfile(token);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping(value = "/profile", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Update user profile", description = "Updates the user profile. Requires JWT token in Authorization header.")
    @Tag(name = "Profile")
    public ResponseEntity<GatewayResponse> updateProfile(
            @RequestParam(name = "email", required = false) String email,
            @Valid @RequestBody UserProfile profile,
            @RequestHeader(name = "Authorization", required = false) String token) {
        
        GatewayResponse response = userManagementService.updateProfile(profile, token);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}

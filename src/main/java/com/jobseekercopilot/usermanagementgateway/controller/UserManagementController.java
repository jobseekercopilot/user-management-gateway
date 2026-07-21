package com.jobseekercopilot.usermanagementgateway.controller;

import com.jobseekercopilot.usermanagementgateway.model.*;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry.UserOperation;
import com.jobseekercopilot.usermanagementgateway.service.UserManagementService;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account and initial profile created"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "409", description = "Account already exists", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "413", description = "Request body too large", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "503", description = "Dependency unavailable", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected internal failure", content = @Content(schema = @Schema(implementation = GatewayResponse.class)))
    })
    @Tag(name = "Authentication")
    public ResponseEntity<GatewayResponse> register(@Valid @RequestBody RegisterRequest request) {
        long startedAt = System.nanoTime();
        GatewayResponse response = userManagementService.register(request);
        telemetry.record(UserOperation.REGISTER, response, System.nanoTime() - startedAt);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping(value = "/login", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Authenticate user", description = "Authenticates a user and returns a JWT token via authentication-service.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "413", description = "Request body too large", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "503", description = "Dependency unavailable", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected internal failure", content = @Content(schema = @Schema(implementation = GatewayResponse.class)))
    })
    @Tag(name = "Authentication")
    public ResponseEntity<GatewayResponse> login(@Valid @RequestBody LoginRequest request) {
        long startedAt = System.nanoTime();
        GatewayResponse response = userManagementService.login(request);
        telemetry.record(UserOperation.LOGIN, response, System.nanoTime() - startedAt);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/profile")
    @Operation(summary = "Get current user profile", description = "Retrieves the authenticated user's profile. Ownership is derived from the bearer token; callers cannot select another user.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current profile returned"),
            @ApiResponse(responseCode = "400", description = "Bearer token missing or malformed", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "401", description = "Bearer token invalid or expired", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "404", description = "Authenticated user not found", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "409", description = "Initial profile write conflict", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "503", description = "Dependency unavailable", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected internal failure", content = @Content(schema = @Schema(implementation = GatewayResponse.class)))
    })
    @Tag(name = "Profile")
    public ResponseEntity<GatewayResponse> getProfile(
            @Parameter(hidden = true) @RequestHeader(name = "Authorization", required = false) String token) {
        
        long startedAt = System.nanoTime();
        GatewayResponse response = userManagementService.getProfile(token);
        telemetry.record(UserOperation.PROFILE_READ, response, System.nanoTime() - startedAt);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping(value = "/profile", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Update current user profile", description = "Replaces the authenticated user's profile. Ownership is derived from the bearer token; callers cannot select another user.")
    @SecurityRequirement(name = "bearerAuth")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current profile updated"),
            @ApiResponse(responseCode = "400", description = "Invalid profile or bearer token", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "401", description = "Bearer token invalid or expired", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "404", description = "Authenticated user not found", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "409", description = "Profile write conflict", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "413", description = "Request body too large", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "503", description = "Dependency unavailable", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected internal failure", content = @Content(schema = @Schema(implementation = GatewayResponse.class)))
    })
    @Tag(name = "Profile")
    public ResponseEntity<GatewayResponse> updateProfile(
            @Valid @RequestBody UserProfile profile,
            @Parameter(hidden = true) @RequestHeader(name = "Authorization", required = false) String token) {
        
        long startedAt = System.nanoTime();
        GatewayResponse response = userManagementService.updateProfile(profile, token);
        telemetry.record(UserOperation.PROFILE_UPDATE, response, System.nanoTime() - startedAt);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }
}

package com.jobseekercopilot.usermanagementgateway.controller;

import com.jobseekercopilot.usermanagementgateway.model.*;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry.UserOperation;
import com.jobseekercopilot.usermanagementgateway.service.UserManagementService;
import com.jobseekercopilot.usermanagementgateway.security.SessionCookieService;
import com.jobseekercopilot.usermanagementgateway.security.RefreshCoordinator;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class UserManagementController {

    @Autowired
    private UserManagementService userManagementService;

    @Autowired
    private GatewayTelemetry telemetry;

    @Autowired
    private SessionCookieService sessionCookies;

    @Autowired
    private RefreshCoordinator refreshCoordinator;

    @Autowired
    private CsrfTokenRepository csrfTokens;

    @GetMapping("/csrf")
    @Operation(summary = "Bootstrap browser CSRF protection")
    public Map<String, String> csrf(HttpServletRequest request, HttpServletResponse response) {
        var token = csrfTokens.loadToken(request);
        if (token == null) {
            token = csrfTokens.generateToken(request);
            csrfTokens.saveToken(token, request, response);
        }
        return Map.of("headerName", token.getHeaderName(), "token", token.getToken());
    }

    @PostMapping(value = "/register", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Register a new user", description = "Registers a new user account. Coordinates with authentication-service to create the user.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Account and initial profile created"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF validation failed", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
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
        var session = userManagementService.registerSession(request);
        GatewayResponse response = session.response();
        telemetry.record(UserOperation.REGISTER, response, System.nanoTime() - startedAt);
        if (session.authenticated()) {
            return sessionCookies.withSession(ResponseEntity.status(response.getStatusCode()), response, session);
        }
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping(value = "/login", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Authenticate user", description = "Authenticates a user and establishes an HttpOnly browser session without returning tokens to JavaScript.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Authenticated"),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "401", description = "Authentication failed", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF validation failed", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "413", description = "Request body too large", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "415", description = "Unsupported media type", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "503", description = "Dependency unavailable", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected internal failure", content = @Content(schema = @Schema(implementation = GatewayResponse.class)))
    })
    @Tag(name = "Authentication")
    public ResponseEntity<GatewayResponse> login(@Valid @RequestBody LoginRequest request) {
        long startedAt = System.nanoTime();
        var session = userManagementService.loginSession(request);
        GatewayResponse response = session.response();
        telemetry.record(UserOperation.LOGIN, response, System.nanoTime() - startedAt);
        if (session.authenticated()) {
            return sessionCookies.withSession(ResponseEntity.status(response.getStatusCode()), response, session);
        }
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @GetMapping("/profile")
    @Operation(summary = "Get current user profile", description = "Retrieves the authenticated user's profile. Ownership is derived from the HttpOnly session cookie; callers cannot select another user.")
    @SecurityRequirement(name = "browserSession")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current profile returned"),
            @ApiResponse(responseCode = "401", description = "Browser session missing, invalid or expired", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "404", description = "Authenticated user not found", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "409", description = "Initial profile write conflict", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "503", description = "Dependency unavailable", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected internal failure", content = @Content(schema = @Schema(implementation = GatewayResponse.class)))
    })
    @Tag(name = "Profile")
    public ResponseEntity<GatewayResponse> getProfile(BearerTokenAuthentication authentication) {
        String token = authentication.getToken().getTokenValue();
        long startedAt = System.nanoTime();
        GatewayResponse response = userManagementService.getProfile(token);
        telemetry.record(UserOperation.PROFILE_READ, response, System.nanoTime() - startedAt);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PutMapping(value = "/profile", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Update current user profile", description = "Replaces the authenticated user's profile. Ownership is derived from the HttpOnly session cookie; callers cannot select another user.")
    @SecurityRequirement(name = "browserSession")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Current profile updated"),
            @ApiResponse(responseCode = "400", description = "Invalid profile", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "401", description = "Browser session missing, invalid or expired", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF validation failed", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
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
            BearerTokenAuthentication authentication) {
        String token = authentication.getToken().getTokenValue();
        long startedAt = System.nanoTime();
        GatewayResponse response = userManagementService.updateProfile(profile, token);
        telemetry.record(UserOperation.PROFILE_UPDATE, response, System.nanoTime() - startedAt);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/refresh")
    @Operation(summary = "Rotate the browser session")
    @SecurityRequirement(name = "browserRefresh")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session rotated"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF validation failed", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "429", description = "Authentication rate limited", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected internal failure", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "503", description = "Dependency or session coordination unavailable", content = @Content(schema = @Schema(implementation = GatewayResponse.class)))
    })
    public ResponseEntity<GatewayResponse> refresh(HttpServletRequest request) {
        var session = refreshCoordinator.refresh(sessionCookies.refreshToken(request));
        GatewayResponse response = session.response();
        if (session.authenticated()) {
            return sessionCookies.withSession(ResponseEntity.ok(), response, session);
        }
        if (response.getStatusCode() == 401) {
            return sessionCookies.clearSession(ResponseEntity.status(401), response);
        }
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping("/logout")
    @Operation(summary = "End the browser session")
    @SecurityRequirement(name = "browserSession")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Session revoked and cookies cleared"),
            @ApiResponse(responseCode = "401", description = "Session missing or expired", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF validation failed", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "500", description = "Unexpected internal failure", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "503", description = "Authentication service unavailable", content = @Content(schema = @Schema(implementation = GatewayResponse.class)))
    })
    public ResponseEntity<GatewayResponse> logout(HttpServletRequest request) {
        GatewayResponse response = userManagementService.logoutSession(sessionCookies.accessToken(request));
        return sessionCookies.clearSession(ResponseEntity.status(response.getStatusCode()), response);
    }
}

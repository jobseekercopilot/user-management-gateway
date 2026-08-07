package com.jobseekercopilot.usermanagementgateway.controller;

import com.jobseekercopilot.generated.userprofileservice.model.EvidenceEntry;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceSupersedeRequest;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceWriteRequest;
import com.jobseekercopilot.generated.userprofileservice.model.ProfilePreferencesUpdate;
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
import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.server.resource.authentication.BearerTokenAuthentication;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.web.bind.annotation.*;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.List;
import java.util.UUID;

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

    @PostMapping(
            value = "/password-reset/request",
            consumes = "application/json",
            produces = "application/json")
    @Operation(
            summary = "Request a password-reset email",
            description = "Always returns the same accepted response for known and unknown accounts.")
    @ApiResponses({
            @ApiResponse(responseCode = "202", description = "Request accepted", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "400", description = "Invalid request", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF validation failed", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "429", description = "Source IP rate limited", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "503", description = "Authentication service unavailable", content = @Content(schema = @Schema(implementation = GatewayResponse.class)))
    })
    @Tag(name = "Authentication")
    public ResponseEntity<GatewayResponse> requestPasswordReset(
            @Valid @RequestBody PasswordResetRequest request) {
        GatewayResponse response = userManagementService.requestPasswordReset(request);
        return ResponseEntity.status(response.getStatusCode()).body(response);
    }

    @PostMapping(
            value = "/password-reset/complete",
            consumes = "application/json",
            produces = "application/json")
    @Operation(
            summary = "Complete a password reset",
            description = "Consumes a one-time reset token and clears any browser session cookies.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Password changed and sessions revoked", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "400", description = "Reset link or new password rejected", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "403", description = "CSRF validation failed", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "429", description = "Source IP rate limited", content = @Content(schema = @Schema(implementation = GatewayResponse.class))),
            @ApiResponse(responseCode = "503", description = "Authentication service unavailable", content = @Content(schema = @Schema(implementation = GatewayResponse.class)))
    })
    @Tag(name = "Authentication")
    public ResponseEntity<GatewayResponse> completePasswordReset(
            @Valid @RequestBody PasswordResetCompletionRequest request) {
        GatewayResponse response = userManagementService.completePasswordReset(request);
        if (response.isSuccess()) {
            return sessionCookies.clearSession(
                    ResponseEntity.status(response.getStatusCode()), response);
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

    @GetMapping(value = "/account/export", produces = "application/json")
    @Operation(
            summary = "Export current account data",
            description = "Returns a no-store machine-readable export for a recently authenticated browser session.")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Account lifecycle")
    public ResponseEntity<com.jobseekercopilot.generated.authenticationservice.model.PersonalDataExport>
            exportPersonalData(BearerTokenAuthentication authentication) {
        var export = userManagementService.exportPersonalData(
                authentication.getToken().getTokenValue());
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"job-seeker-copilot-personal-data.json\"")
                .body(export);
    }

    @DeleteMapping("/account")
    @Operation(
            summary = "Delete current account and owned data",
            description = "Starts the authenticated retry-safe deletion workflow and clears browser credentials.")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Account lifecycle")
    public ResponseEntity<com.jobseekercopilot.generated.authenticationservice.model.AccountDeletionResponse>
            deleteAccount(
                    @RequestHeader("Idempotency-Key") String idempotencyKey,
                    BearerTokenAuthentication authentication) {
        var response = userManagementService.deleteAccount(
                authentication.getToken().getTokenValue(), idempotencyKey);
        return sessionCookies.clearSession(
                ResponseEntity.accepted()
                        .cacheControl(org.springframework.http.CacheControl.noStore()),
                response);
    }

    @PatchMapping(value = "/profile", consumes = "application/json", produces = "application/json")
    @Operation(
            summary = "Update job-search preferences and reusable skills",
            description = "Updates current job-search preferences and canonical reusable skills without replacing versioned career evidence or legacy history. Omitted or null skills preserve the current catalogue; an explicit empty list clears it.")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Profile")
    public ResponseEntity<GatewayResponse> updatePreferences(
            @Valid @RequestBody ProfilePreferencesUpdate update,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            BearerTokenAuthentication authentication) {
        GatewayResponse response = userManagementService.updatePreferences(
                update, authentication.getToken().getTokenValue(), ifMatch);
        ResponseEntity.BodyBuilder builder = ResponseEntity.status(response.getStatusCode());
        if (response.isSuccess()
                && response.getUser() != null
                && response.getUser().getProfile() != null
                && response.getUser().getProfile().getRevision() != null) {
            builder.eTag(Long.toString(response.getUser().getProfile().getRevision()));
        }
        return builder.body(response);
    }

    @GetMapping(value = "/evidence", produces = "application/json")
    @Operation(summary = "List current claimant evidence")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Evidence Library")
    public ResponseEntity<List<EvidenceEntry>> listEvidence(
            @RequestParam(defaultValue = "false") boolean includeArchived,
            BearerTokenAuthentication authentication) {
        return userManagementService.listEvidence(
                authentication.getToken().getTokenValue(), includeArchived);
    }

    @GetMapping(value = "/evidence/{entryId}", produces = "application/json")
    @Operation(summary = "Get one claimant-owned evidence entry")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Evidence Library")
    public ResponseEntity<EvidenceEntry> getEvidence(
            @PathVariable UUID entryId,
            BearerTokenAuthentication authentication) {
        return userManagementService.getEvidence(
                authentication.getToken().getTokenValue(), entryId);
    }

    @PostMapping(value = "/evidence", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Create a draft evidence entry")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Evidence Library")
    public ResponseEntity<EvidenceEntry> createEvidence(
            @Valid @RequestBody EvidenceWriteRequest request,
            BearerTokenAuthentication authentication) {
        return userManagementService.createEvidence(
                authentication.getToken().getTokenValue(), request);
    }

    @PutMapping(value = "/evidence/{entryId}", consumes = "application/json", produces = "application/json")
    @Operation(summary = "Create an edited draft evidence revision")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Evidence Library")
    public ResponseEntity<EvidenceEntry> updateEvidence(
            @PathVariable UUID entryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody EvidenceWriteRequest request,
            BearerTokenAuthentication authentication) {
        return userManagementService.updateEvidence(
                authentication.getToken().getTokenValue(), entryId, ifMatch, request);
    }

    @PostMapping(value = "/evidence/{entryId}/confirm", produces = "application/json")
    @Operation(summary = "Confirm the latest draft evidence revision")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Evidence Library")
    public ResponseEntity<EvidenceEntry> confirmEvidence(
            @PathVariable UUID entryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            BearerTokenAuthentication authentication) {
        return userManagementService.confirmEvidence(
                authentication.getToken().getTokenValue(), entryId, ifMatch);
    }

    @PostMapping(value = "/evidence/{entryId}/hide", produces = "application/json")
    @Operation(summary = "Hide an evidence entry")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Evidence Library")
    public ResponseEntity<EvidenceEntry> hideEvidence(
            @PathVariable UUID entryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            BearerTokenAuthentication authentication) {
        return userManagementService.hideEvidence(
                authentication.getToken().getTokenValue(), entryId, ifMatch);
    }

    @PostMapping(value = "/evidence/{entryId}/show", produces = "application/json")
    @Operation(summary = "Show an evidence entry")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Evidence Library")
    public ResponseEntity<EvidenceEntry> showEvidence(
            @PathVariable UUID entryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            BearerTokenAuthentication authentication) {
        return userManagementService.showEvidence(
                authentication.getToken().getTokenValue(), entryId, ifMatch);
    }

    @PostMapping(value = "/evidence/{entryId}/archive", produces = "application/json")
    @Operation(summary = "Archive an evidence entry")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Evidence Library")
    public ResponseEntity<EvidenceEntry> archiveEvidence(
            @PathVariable UUID entryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            BearerTokenAuthentication authentication) {
        return userManagementService.archiveEvidence(
                authentication.getToken().getTokenValue(), entryId, ifMatch);
    }

    @PostMapping(value = "/evidence/{entryId}/restore", produces = "application/json")
    @Operation(summary = "Restore an archived evidence entry")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Evidence Library")
    public ResponseEntity<EvidenceEntry> restoreEvidence(
            @PathVariable UUID entryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            BearerTokenAuthentication authentication) {
        return userManagementService.restoreEvidence(
                authentication.getToken().getTokenValue(), entryId, ifMatch);
    }

    @PostMapping(
            value = "/evidence/{entryId}/supersede",
            consumes = "application/json",
            produces = "application/json")
    @Operation(summary = "Supersede an evidence entry with another claimant-owned entry")
    @SecurityRequirement(name = "browserSession")
    @Tag(name = "Evidence Library")
    public ResponseEntity<EvidenceEntry> supersedeEvidence(
            @PathVariable UUID entryId,
            @RequestHeader(value = HttpHeaders.IF_MATCH, required = false) String ifMatch,
            @Valid @RequestBody EvidenceSupersedeRequest request,
            BearerTokenAuthentication authentication) {
        return userManagementService.supersedeEvidence(
                authentication.getToken().getTokenValue(), entryId, ifMatch, request);
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

package com.jobseekercopilot.usermanagementgateway.service;

import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.jobseekercopilot.generated.authenticationservice.api.AuthenticationApi;
import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import com.jobseekercopilot.generated.userprofileservice.api.EvidenceLibraryApi;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceEntry;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceSupersedeRequest;
import com.jobseekercopilot.generated.userprofileservice.model.EvidenceWriteRequest;
import com.jobseekercopilot.generated.userprofileservice.model.ProfilePreferencesUpdate;
import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.model.LoginRequest;
import com.jobseekercopilot.usermanagementgateway.model.PasswordResetCompletionRequest;
import com.jobseekercopilot.usermanagementgateway.model.PasswordResetRequest;
import com.jobseekercopilot.usermanagementgateway.model.RegisterRequest;
import com.jobseekercopilot.usermanagementgateway.model.User;
import com.jobseekercopilot.usermanagementgateway.model.UserProfile;
import com.jobseekercopilot.usermanagementgateway.model.Qualification;
import com.jobseekercopilot.usermanagementgateway.model.Role;
import com.jobseekercopilot.usermanagementgateway.model.SessionOutcome;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;

@Service
@Primary
public class UserManagementService implements IUserManagementService {

    private static final Logger log = LoggerFactory.getLogger(UserManagementService.class);
    private static final Pattern EMAIL = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");

    @Autowired
    private AuthenticationApi authenticationApi;

    @Autowired
    private UserProfilesApi userProfilesApi;

    @Autowired
    private EvidenceLibraryApi evidenceLibraryApi;

    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @Override
    public GatewayResponse register(RegisterRequest request) {
        return registerSession(request).response();
    }

    public SessionOutcome registerSession(RegisterRequest request) {
        long startedAt = System.nanoTime();
        log.info("user-management-gateway registration received hasProfile={}",
                request != null && request.getProfile() != null);
        if (request == null) {
            return SessionOutcome.failure(invalidRequest());
        }

        String name = request.getName();
        String email = request.getEmail();
        String password = request.getPassword();

        if (!hasCodePointLength(name, 1, 100)
                || !hasCodePointLength(email, 1, 254) || !EMAIL.matcher(email).matches()
                || !hasCodePointLength(password, 15, 128)) {
            return SessionOutcome.failure(invalidRequest());
        }

        try {
            long registerStartedAt = System.nanoTime();
            log.info("Calling authentication-service register");
            authenticationApi.register(new com.jobseekercopilot.generated.authenticationservice.model.RegisterRequest()
                    .name(name).email(email).password(password));
            log.info("authentication-service register returned durationMs={}",
                    (System.nanoTime() - registerStartedAt) / 1_000_000);

            long loginStartedAt = System.nanoTime();
            log.info("Calling authentication-service login after registration");
            var loginResponse = authenticationApi.login(
                    new com.jobseekercopilot.generated.authenticationservice.model.LoginRequest()
                            .email(email).password(password));
            log.info("authentication-service login returned durationMs={}",
                    (System.nanoTime() - loginStartedAt) / 1_000_000);
            var userAccountResponse = getUser(loginResponse.getToken());
            
            UserProfile initialProfile = request.getProfile() != null ? request.getProfile() : new UserProfile(
            java.util.Collections.<String>emptyList(), // skills: List<String>
            java.util.Collections.<Qualification>emptyList(), // qualifications: List<Qualification>
            java.util.Collections.<Role>emptyList(), // roles: List<Role>
            null, // aspirations
            null  // workPreferences
        );
            UserProfile userProfile = createOrUpdateProfile(loginResponse.getToken(), initialProfile);

            User user = new User(userAccountResponse.getId(), name, email, userProfile);
            log.info("user-management-gateway registration completed durationMs={}",
                    (System.nanoTime() - startedAt) / 1_000_000);

            return sessionOutcome(new GatewayResponse(201, true,
                    "Claimant account registered securely with the User Management Gateway.", user), loginResponse);
        } catch (ResourceAccessException | HttpServerErrorException ex) {
            log.warn("user-management-gateway registration dependency unavailable durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName());
            return SessionOutcome.failure(dependencyUnavailable());
        } catch (HttpClientErrorException ex) {
            log.warn("user-management-gateway registration failed status={} durationMs={}",
                    ex.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return SessionOutcome.failure(downstreamRejected(ex));
        } catch (Exception ex) {
            log.error("user-management-gateway registration failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName());
            return SessionOutcome.failure(internalError());
        }
    }

    @Override
    public GatewayResponse login(LoginRequest request) {
        return loginSession(request).response();
    }

    public SessionOutcome loginSession(LoginRequest request) {
        long startedAt = System.nanoTime();
        log.info("user-management-gateway login received hasRequest={}", request != null);
        if (request == null) {
            return SessionOutcome.failure(invalidRequest());
        }

        String email = request.getEmail();
        String password = request.getPassword();

        if (!hasCodePointLength(email, 1, 254) || !EMAIL.matcher(email).matches()
                || !hasCodePointLength(password, 1, 128)) {
            return SessionOutcome.failure(invalidRequest());
        }

        try {
            long loginStartedAt = System.nanoTime();
            log.info("Calling authentication-service login");
            var loginResponse = authenticationApi.login(
                    new com.jobseekercopilot.generated.authenticationservice.model.LoginRequest()
                            .email(email).password(password));
            log.info("authentication-service login returned durationMs={}",
                    (System.nanoTime() - loginStartedAt) / 1_000_000);
            var userAccountResponse = getUser(loginResponse.getToken());
            String userId = userAccountResponse.getId();

            // Resilient fallback using the updated 4-string constructor if no profile exists yet
            UserProfile profile;
            try {
                profile = getProfileByAccessToken(loginResponse.getToken());
            } catch (HttpClientErrorException.NotFound ex) {
                profile = new UserProfile(
                    java.util.Collections.<String>emptyList(),
                    java.util.Collections.<Qualification>emptyList(),
                    java.util.Collections.<Role>emptyList(),
                    null,
                    null
                );
            }

            User user = new User(userId, userAccountResponse.getName(), userAccountResponse.getEmail(), profile);
            log.info("user-management-gateway login completed durationMs={}",
                    (System.nanoTime() - startedAt) / 1_000_000);

            return sessionOutcome(new GatewayResponse(200, true,
                    "Credentials verified and secure handshake completed by gateway.", user), loginResponse);
        } catch (ResourceAccessException | HttpServerErrorException ex) {
            log.warn("user-management-gateway login dependency unavailable durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName());
            return SessionOutcome.failure(dependencyUnavailable());
        } catch (HttpClientErrorException ex) {
            log.warn("user-management-gateway login failed status={} durationMs={}",
                    ex.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return SessionOutcome.failure(downstreamRejected(ex));
        } catch (Exception ex) {
            log.error("user-management-gateway login failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName());
            return SessionOutcome.failure(internalError());
        }
    }

    @Override
    public GatewayResponse getProfile(String token) {
        long startedAt = System.nanoTime();
        log.info("user-management-gateway profile request received hasToken={}", token != null && !token.isBlank());
        if (token == null || token.trim().isEmpty()) {
            return new GatewayResponse(400, false, "Authorization token must be supplied.");
        }

        try {
            var userAccount = getUser(token);
            String userId = userAccount.getId();

            UserProfile profile;
            try {
                profile = getProfileByAccessToken(token);
            } catch (HttpClientErrorException.NotFound ex) {
                // Creates a blank 4-field profile if missing downstream
                UserProfile initialProfile = new UserProfile(
                java.util.Collections.<String>emptyList(),
                java.util.Collections.<Qualification>emptyList(),
                java.util.Collections.<Role>emptyList(),
                null,
                null
);
                profile = createOrUpdateProfile(token, initialProfile);
            }

            User user = new User(userId, userAccount.getName(), userAccount.getEmail(), profile);
            log.info("user-management-gateway profile request completed durationMs={}",
                    (System.nanoTime() - startedAt) / 1_000_000);
            return new GatewayResponse(200, true, "User profile retrieved successfully from the gateway.", user);
        } catch (ResourceAccessException | HttpServerErrorException ex) {
            log.warn("user-management-gateway profile dependency unavailable durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName());
            return dependencyUnavailable();
        } catch (HttpClientErrorException ex) {
            log.warn("user-management-gateway profile request failed status={} durationMs={}",
                    ex.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return downstreamRejected(ex);
        } catch (Exception ex) {
            log.error("user-management-gateway profile request failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName());
            return internalError();
        }
    }

    @Override
    public GatewayResponse updateProfile(UserProfile profile, String token) {
        long startedAt = System.nanoTime();
        log.info("user-management-gateway profile update received skillsCount={} qualificationsCount={} rolesCount={}",
                profile == null || profile.getSkills() == null ? 0 : profile.getSkills().size(),
                profile == null || profile.getQualifications() == null ? 0 : profile.getQualifications().size(),
                profile == null || profile.getRoles() == null ? 0 : profile.getRoles().size());
        if (profile == null) {
            return new GatewayResponse(400, false, "Profile body is required.");
        }

        if (token == null || token.trim().isEmpty()) {
            return new GatewayResponse(400, false, "Token is required.");
        }

        try {
            var userAccountResponse = getUser(token);
            UserProfile updatedProfile = createOrUpdateProfile(token, profile);

            User user = new User(userAccountResponse.getId(), userAccountResponse.getName(), userAccountResponse.getEmail(), updatedProfile);
            log.info("user-management-gateway profile update completed durationMs={}",
                    (System.nanoTime() - startedAt) / 1_000_000);
            return new GatewayResponse(200, true, "User profile updated successfully in gateway and profile service.", user);
        } catch (ResourceAccessException | HttpServerErrorException ex) {
            log.warn("user-management-gateway profile update dependency unavailable durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName());
            return dependencyUnavailable();
        } catch (HttpClientErrorException ex) {
            log.warn("user-management-gateway profile update failed status={} durationMs={}",
                    ex.getStatusCode().value(),
                    (System.nanoTime() - startedAt) / 1_000_000);
            return downstreamRejected(ex);
        } catch (Exception ex) {
            log.error("user-management-gateway profile update failed durationMs={} error={}",
                    (System.nanoTime() - startedAt) / 1_000_000,
                    ex.getClass().getSimpleName());
            return internalError();
        }
    }

    public GatewayResponse updatePreferences(
            ProfilePreferencesUpdate update,
            String token,
            String ifMatch) {
        if (update == null || token == null || token.isBlank()) {
            return invalidRequest();
        }
        try {
            var userAccount = getUser(token);
            var downstream = userProfilesApi.updateMyPreferences(
                    bearer(token), update, ifMatch);
            UserProfile profile = objectMapper.convertValue(downstream, UserProfile.class);
            User user = new User(
                    userAccount.getId(), userAccount.getName(), userAccount.getEmail(), profile);
            return new GatewayResponse(
                    200, true, "Profile preferences updated successfully.", user);
        } catch (HttpServerErrorException exception) {
            log.warn("profile preference update dependency failed status={}",
                    exception.getStatusCode().value());
            return dependencyUnavailable();
        } catch (ResourceAccessException exception) {
            log.warn("profile preference update dependency unavailable error={}",
                    exception.getClass().getSimpleName());
            return dependencyUnavailable();
        } catch (HttpClientErrorException exception) {
            return downstreamRejected(exception);
        } catch (Exception exception) {
            log.error("profile preference update failed error={}", exception.getClass().getSimpleName());
            return internalError();
        }
    }

    public ResponseEntity<List<EvidenceEntry>> listEvidence(
            String token,
            boolean includeArchived) {
        return evidenceLibraryApi.listEvidenceWithHttpInfo(
                bearer(token), includeArchived);
    }

    public ResponseEntity<EvidenceEntry> getEvidence(String token, UUID entryId) {
        return evidenceLibraryApi.getEvidenceWithHttpInfo(bearer(token), entryId);
    }

    public ResponseEntity<EvidenceEntry> createEvidence(
            String token,
            EvidenceWriteRequest request) {
        return evidenceLibraryApi.createEvidenceWithHttpInfo(bearer(token), request);
    }

    public ResponseEntity<EvidenceEntry> updateEvidence(
            String token,
            UUID entryId,
            String ifMatch,
            EvidenceWriteRequest request) {
        return evidenceLibraryApi.updateEvidenceWithHttpInfo(
                bearer(token), entryId, request, ifMatch);
    }

    public ResponseEntity<EvidenceEntry> confirmEvidence(
            String token,
            UUID entryId,
            String ifMatch) {
        return evidenceLibraryApi.confirmEvidenceWithHttpInfo(bearer(token), entryId, ifMatch);
    }

    public ResponseEntity<EvidenceEntry> hideEvidence(
            String token,
            UUID entryId,
            String ifMatch) {
        return evidenceLibraryApi.hideEvidenceWithHttpInfo(bearer(token), entryId, ifMatch);
    }

    public ResponseEntity<EvidenceEntry> showEvidence(
            String token,
            UUID entryId,
            String ifMatch) {
        return evidenceLibraryApi.showEvidenceWithHttpInfo(bearer(token), entryId, ifMatch);
    }

    public ResponseEntity<EvidenceEntry> archiveEvidence(
            String token,
            UUID entryId,
            String ifMatch) {
        return evidenceLibraryApi.archiveEvidenceWithHttpInfo(bearer(token), entryId, ifMatch);
    }

    public ResponseEntity<EvidenceEntry> restoreEvidence(
            String token,
            UUID entryId,
            String ifMatch) {
        return evidenceLibraryApi.restoreEvidenceWithHttpInfo(bearer(token), entryId, ifMatch);
    }

    public ResponseEntity<EvidenceEntry> supersedeEvidence(
            String token,
            UUID entryId,
            String ifMatch,
            EvidenceSupersedeRequest request) {
        return evidenceLibraryApi.supersedeEvidenceWithHttpInfo(
                bearer(token), entryId, request, ifMatch);
    }

    public GatewayResponse requestPasswordReset(PasswordResetRequest request) {
        if (request == null || !hasCodePointLength(request.getEmail(), 1, 254)
                || !EMAIL.matcher(request.getEmail()).matches()) {
            return invalidRequest();
        }
        try {
            authenticationApi.requestPasswordReset(
                    new com.jobseekercopilot.generated.authenticationservice.model.PasswordResetRequest()
                            .email(request.getEmail()));
            return new GatewayResponse(
                    202, true,
                    "If an account exists for that email, a password-reset link has been sent.");
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("password-reset request dependency unavailable error={}",
                    exception.getClass().getSimpleName());
            return dependencyUnavailable();
        } catch (HttpClientErrorException exception) {
            log.warn("password-reset request rejected status={}",
                    exception.getStatusCode().value());
            return downstreamRejected(exception);
        } catch (Exception exception) {
            log.error("password-reset request failed error={}",
                    exception.getClass().getSimpleName());
            return internalError();
        }
    }

    public GatewayResponse completePasswordReset(PasswordResetCompletionRequest request) {
        if (request == null
                || !hasCodePointLength(request.getToken(), 32, 128)
                || !request.getToken().matches("^[A-Za-z0-9_-]+$")
                || !hasCodePointLength(request.getNewPassword(), 15, 128)) {
            return invalidRequest();
        }
        try {
            authenticationApi.completePasswordReset(
                    new com.jobseekercopilot.generated.authenticationservice.model.PasswordResetCompletionRequest()
                            .token(request.getToken())
                            .newPassword(request.getNewPassword()));
            return new GatewayResponse(
                    200, true,
                    "Your password has been changed. Sign in with your new password.");
        } catch (ResourceAccessException | HttpServerErrorException exception) {
            log.warn("password-reset completion dependency unavailable error={}",
                    exception.getClass().getSimpleName());
            return dependencyUnavailable();
        } catch (HttpClientErrorException.BadRequest exception) {
            log.warn("password-reset completion rejected status=400");
            return GatewayResponse.failure(
                    400,
                    "PASSWORD_RESET_REJECTED",
                    "The reset link or new password could not be accepted.");
        } catch (HttpClientErrorException exception) {
            log.warn("password-reset completion rejected status={}",
                    exception.getStatusCode().value());
            return downstreamRejected(exception);
        } catch (Exception exception) {
            log.error("password-reset completion failed error={}",
                    exception.getClass().getSimpleName());
            return internalError();
        }
    }

    private GatewayResponse downstreamRejected(HttpClientErrorException ex) {
        int status = ex.getStatusCode().value();
        return switch (status) {
            case 400 -> GatewayResponse.failure(400, "DOWNSTREAM_VALIDATION_FAILED", "Request validation failed.");
            case 401 -> GatewayResponse.failure(401, "AUTHENTICATION_FAILED", "Invalid email or password.");
            case 404 -> GatewayResponse.failure(404, "NOT_FOUND", "The requested resource was not found.");
            case 409 -> GatewayResponse.failure(409, "ACCOUNT_ALREADY_EXISTS", "An account with this email already exists.");
            case 429 -> GatewayResponse.failure(429, "TOO_MANY_AUTHENTICATION_ATTEMPTS",
                    "Too many authentication attempts. Try again later.");
            default -> GatewayResponse.failure(status, "DOWNSTREAM_REQUEST_REJECTED", "The request was rejected.");
        };
    }

    private GatewayResponse dependencyUnavailable() {
        return GatewayResponse.failure(503, "DEPENDENCY_UNAVAILABLE", "A required service is temporarily unavailable.");
    }

    private GatewayResponse invalidRequest() {
        return GatewayResponse.failure(400, "REQUEST_VALIDATION_FAILED", "Request validation failed.");
    }

    private GatewayResponse internalError() {
        return GatewayResponse.failure(500, "INTERNAL_ERROR", "An unexpected error occurred.");
    }

    public SessionOutcome refreshSession(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return SessionOutcome.failure(GatewayResponse.failure(
                    401, "SESSION_REQUIRED", "The browser session is not authenticated."));
        }
        try {
            var refreshed = authenticationApi.refresh(
                    new com.jobseekercopilot.generated.authenticationservice.model.RefreshRequest()
                            .refreshToken(refreshToken));
            return sessionOutcome(new GatewayResponse(
                    200, true, "Browser session refreshed."), refreshed);
        } catch (HttpClientErrorException ex) {
            return SessionOutcome.failure(GatewayResponse.failure(
                    401, "SESSION_EXPIRED", "The browser session has expired."));
        } catch (ResourceAccessException | HttpServerErrorException ex) {
            return SessionOutcome.failure(dependencyUnavailable());
        } catch (Exception ex) {
            return SessionOutcome.failure(internalError());
        }
    }

    public GatewayResponse logoutSession(String accessToken) {
        if (accessToken == null || accessToken.isBlank()) {
            return GatewayResponse.failure(401, "SESSION_REQUIRED", "The browser session is not authenticated.");
        }
        try {
            authenticationApi.logout("Bearer " + cleanToken(accessToken));
            return new GatewayResponse(200, true, "Browser session ended.");
        } catch (HttpClientErrorException.Unauthorized ex) {
            return GatewayResponse.failure(401, "SESSION_EXPIRED", "The browser session has expired.");
        } catch (ResourceAccessException | HttpServerErrorException ex) {
            return dependencyUnavailable();
        } catch (Exception ex) {
            return internalError();
        }
    }

    public com.jobseekercopilot.generated.authenticationservice.model.UserAccountResponse authenticate(
            String accessToken) {
        return getUser(accessToken);
    }

    private SessionOutcome sessionOutcome(
            GatewayResponse response,
            com.jobseekercopilot.generated.authenticationservice.model.LoginResponse loginResponse) {
        if (loginResponse == null || loginResponse.getToken() == null
                || loginResponse.getToken().isBlank()
                || loginResponse.getRefreshToken() == null
                || loginResponse.getRefreshToken().isBlank()
                || loginResponse.getExpiresIn() == null
                || loginResponse.getExpiresIn() < 1) {
            return SessionOutcome.failure(internalError());
        }
        return new SessionOutcome(response, loginResponse.getToken(), loginResponse.getRefreshToken(),
                loginResponse.getExpiresIn());
    }

    private boolean hasCodePointLength(String value, int minimum, int maximum) {
        if (value == null) {
            return false;
        }
        int count = value.codePointCount(0, value.length());
        return count >= minimum && count <= maximum;
    }

    private com.jobseekercopilot.generated.authenticationservice.model.UserAccountResponse getUser(
            String token) {
        if (token == null || token.isBlank()) {
            throw new IllegalArgumentException("Token cannot be null or empty");
        }
        String cleanToken = cleanToken(token);
        long startedAt = System.nanoTime();
        log.info("Calling authentication-service current user");
        var response = authenticationApi.getCurrentUser("Bearer " + cleanToken);
        log.info("authentication-service current user returned durationMs={}",
                (System.nanoTime() - startedAt) / 1_000_000);
        return response;
    }

    private String cleanToken(String token) {
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }

    private String bearer(String token) {
        return "Bearer " + cleanToken(token);
    }

    private UserProfile getProfileByAccessToken(String accessToken) {
        long startedAt = System.nanoTime();
        log.info("Calling user-profile-service get profile");
        var downstream = userProfilesApi.getMyProfile("Bearer " + cleanToken(accessToken));
        UserProfile profile = objectMapper.convertValue(downstream, UserProfile.class);
        log.info("user-profile-service get profile returned durationMs={}",
                (System.nanoTime() - startedAt) / 1_000_000);
        return profile;
    }

    private UserProfile createOrUpdateProfile(String accessToken, UserProfile profile) {
        long startedAt = System.nanoTime();
        log.info("Calling user-profile-service save profile skillsCount={} qualificationsCount={} rolesCount={}",
                profile == null || profile.getSkills() == null ? 0 : profile.getSkills().size(),
                profile == null || profile.getQualifications() == null ? 0 : profile.getQualifications().size(),
                profile == null || profile.getRoles() == null ? 0 : profile.getRoles().size());
        var downstreamRequest = objectMapper.convertValue(
                profile,
                com.jobseekercopilot.generated.userprofileservice.model.UserProfile.class);
        var downstreamResponse = userProfilesApi.createOrUpdateMyProfile(
                bearer(accessToken), downstreamRequest, null);
        UserProfile savedProfile = objectMapper.convertValue(downstreamResponse, UserProfile.class);
        log.info("user-profile-service save profile returned durationMs={}",
                (System.nanoTime() - startedAt) / 1_000_000);
        return savedProfile;
    }
}

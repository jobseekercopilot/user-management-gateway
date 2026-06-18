package com.jobseekercopilot.usermanagementgateway.client;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import com.jobseekercopilot.usermanagementgateway.model.LoginResponse;
import com.jobseekercopilot.usermanagementgateway.model.UserAccountResponse;

@ExtendWith(MockitoExtension.class)
class AuthenticationClientTest {

    @InjectMocks
    private AuthenticationClient authenticationClient;

    @Mock
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(authenticationClient, "authServiceUrl", "http://localhost:8080");
        ReflectionTestUtils.setField(authenticationClient, "restClient", restClient);
    }

    @Test
    void register_Success() {
        // Arrange
        String name = "candidate";
        String email = "candidate@example.com";
        String password = "securePassword123";

        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/api/auth/register"))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(anyMap())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.toBodilessEntity()).thenReturn(ResponseEntity.ok().build());

        // Act & Assert
        assertDoesNotThrow(() -> authenticationClient.register(name, email, password));
    }

    @Test
    void login_Success() {
        // Arrange
        String email = "candidate@example.com";
        String password = "securePassword123";

        LoginResponse mockResponse = new LoginResponse("mocked-jwt-token");

        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.post()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri(eq("/api/auth/login"))).thenReturn(requestBodySpec);
        when(requestBodySpec.body(anyMap())).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(LoginResponse.class)).thenReturn(mockResponse);

        // Act
        LoginResponse result = authenticationClient.login(email, password);

        // Assert
        assertNotNull(result);
        assertEquals("mocked-jwt-token", result.getToken());
    }

    @Test
    void getUser_Success_WithBearerPrefix() {
        // Arrange
        String rawToken = "Bearer manual-token-123";
        UserAccountResponse mockUser = new UserAccountResponse("user-id-999", "John Doe", "candidate@example.com");

        RestClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/api/auth/me"))).thenReturn(requestHeadersSpec);
        // ✅ Using any() for the second argument safely bypasses the Java Varargs array match issue
        when(requestHeadersSpec.header(eq("Authorization"), any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(UserAccountResponse.class)).thenReturn(mockUser);

        // Act
        UserAccountResponse result = authenticationClient.getUser(rawToken);

        // Assert
        assertNotNull(result);
        assertEquals("user-id-999", result.getId());
        assertEquals("John Doe", result.getName());
    }

    @Test
    void getUser_Success_WithoutBearerPrefix() {
        // Arrange
        String cleanToken = "raw-token-abc";
        UserAccountResponse mockUser = new UserAccountResponse("user-id-777", "Jane Doe", "jane@example.com");

        RestClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri(eq("/api/auth/me"))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("Authorization"), any())).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(UserAccountResponse.class)).thenReturn(mockUser);

        // Act
        UserAccountResponse result = authenticationClient.getUser(cleanToken);

        // Assert
        assertNotNull(result);
        assertEquals("user-id-777", result.getId());
    }

    @Test
    void getUser_ThrowsIllegalArgumentException_WhenTokenIsNullOrEmpty() {
        // Act & Assert for null token
        IllegalArgumentException nullException = assertThrows(IllegalArgumentException.class, () -> {
            authenticationClient.getUser(null);
        });
        assertEquals("Token cannot be null or empty", nullException.getMessage());

        // Act & Assert for empty/blank token
        IllegalArgumentException emptyException = assertThrows(IllegalArgumentException.class, () -> {
            authenticationClient.getUser("   ");
        });
        assertEquals("Token cannot be null or empty", emptyException.getMessage());
    }
}
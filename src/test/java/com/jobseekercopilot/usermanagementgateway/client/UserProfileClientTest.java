package com.jobseekercopilot.usermanagementgateway.client;

import com.jobseekercopilot.usermanagementgateway.model.UserProfile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UserProfileClientTest {

    @InjectMocks
    private UserProfileClient userProfileClient;

    @Mock
    private RestClient restClient;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(userProfileClient, "userProfileServiceUrl", "http://localhost:8085");
        ReflectionTestUtils.setField(userProfileClient, "restClient", restClient);
    }

    @Test
    void getProfile_Success() {
        String userId = "user-123";
        UserProfile mockProfile = new UserProfile();
        mockProfile.setSkills("Java");
        mockProfile.setExperience("5 years");
        mockProfile.setAspirations("Lead");
        mockProfile.setWorkPrefs("Remote");

        RestClient.RequestHeadersUriSpec requestHeadersUriSpec = mock(RestClient.RequestHeadersUriSpec.class);
        RestClient.RequestHeadersSpec requestHeadersSpec = mock(RestClient.RequestHeadersSpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.get()).thenReturn(requestHeadersUriSpec);
        when(requestHeadersUriSpec.uri("/api/profiles/me")).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.header(eq("X-User-Id"), eq(userId))).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.accept(MediaType.APPLICATION_JSON)).thenReturn(requestHeadersSpec);
        when(requestHeadersSpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(UserProfile.class)).thenReturn(mockProfile);

        UserProfile result = userProfileClient.getProfile(userId);

        assertNotNull(result);
        assertEquals("Java", result.getSkills());
        assertEquals("5 years", result.getExperience());
    }

    @Test
    void createOrUpdateProfile_Success() {
        String userId = "user-123";
        UserProfile profile = new UserProfile();
        profile.setSkills("Kotlin");
        profile.setExperience("3 years");
        profile.setAspirations("Senior");
        profile.setWorkPrefs("Remote");

        UserProfile savedProfile = new UserProfile();
        savedProfile.setSkills("Kotlin");
        savedProfile.setExperience("3 years");
        savedProfile.setAspirations("Senior");
        savedProfile.setWorkPrefs("Remote");

        RestClient.RequestBodyUriSpec requestBodyUriSpec = mock(RestClient.RequestBodyUriSpec.class);
        RestClient.RequestBodySpec requestBodySpec = mock(RestClient.RequestBodySpec.class);
        RestClient.ResponseSpec responseSpec = mock(RestClient.ResponseSpec.class);

        when(restClient.put()).thenReturn(requestBodyUriSpec);
        when(requestBodyUriSpec.uri("/api/profiles/me")).thenReturn(requestBodySpec);
        when(requestBodySpec.header(eq("X-User-Id"), eq(userId))).thenReturn(requestBodySpec);
        when(requestBodySpec.contentType(MediaType.APPLICATION_JSON)).thenReturn(requestBodySpec);
        when(requestBodySpec.body(any(UserProfile.class))).thenReturn(requestBodySpec);
        when(requestBodySpec.retrieve()).thenReturn(responseSpec);
        when(responseSpec.body(UserProfile.class)).thenReturn(savedProfile);

        UserProfile result = userProfileClient.createOrUpdateProfile(userId, profile);

        assertNotNull(result);
        assertEquals("Kotlin", result.getSkills());
    }
}
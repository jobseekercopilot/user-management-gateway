package com.jobseekercopilot.usermanagementgateway.controller;

import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.model.LoginRequest;
import com.jobseekercopilot.usermanagementgateway.model.RegisterRequest;
import com.jobseekercopilot.usermanagementgateway.observability.GatewayTelemetry;
import com.jobseekercopilot.usermanagementgateway.service.UserManagementService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(UserManagementController.class)
@TestPropertySource(properties = "gateway.request.maximum-body-bytes=512")
class ControllerValidationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private UserManagementService userManagementService;

    @MockBean
    private GatewayTelemetry telemetry;

    @Test
    void rejectsShortPasswordWithVersionedFieldError() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Example User","email":"user@example.test","password":"too-short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.schemaVersion").value("1"))
                .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.violations[0].field").value("password"))
                .andExpect(jsonPath("$.error.violations[0].code").value("UNICODELENGTH"));

        verifyNoInteractions(userManagementService);
    }

    @Test
    void acceptsFifteenUnicodeCodePointsAndNormalizesIdentityWhitespace() throws Exception {
        when(userManagementService.register(any())).thenReturn(new GatewayResponse(201, true, "Registered"));

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":" Example User ","email":" user@example.test ","password":"🌱🌱🌱🌱🌱🌱🌱🌱🌱🌱🌱🌱🌱🌱🌱"}
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<RegisterRequest> request = ArgumentCaptor.forClass(RegisterRequest.class);
        verify(userManagementService).register(request.capture());
        assertEquals("Example User", request.getValue().getName());
        assertEquals("user@example.test", request.getValue().getEmail());
    }

    @Test
    void rejectsPasswordAboveUnicodeMaximum() throws Exception {
        String password = "x".repeat(129);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Example User","email":"user@example.test","password":"%s"}
                                """.formatted(password)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.violations[0].field").value("password"));

        verifyNoInteractions(userManagementService);
    }

    @Test
    void normalizesLoginEmailWithoutChangingPassword() throws Exception {
        when(userManagementService.login(any())).thenReturn(new GatewayResponse(200, true, "Logged in"));
        String password = "  A legacy passphrase  ";

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":" user@example.test ","password":"%s"}
                                """.formatted(password)))
                .andExpect(status().isOk());

        ArgumentCaptor<LoginRequest> request = ArgumentCaptor.forClass(LoginRequest.class);
        verify(userManagementService).login(request.capture());
        assertEquals("user@example.test", request.getValue().getEmail());
        assertEquals(password, request.getValue().getPassword());
    }

    @Test
    void rejectsMalformedJsonWithoutParserDetails() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{not-json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Request body is not valid JSON."))
                .andExpect(jsonPath("$.error.code").value("MALFORMED_JSON"));
    }

    @Test
    void rejectsUnsupportedContentType() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("credentials"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.error.code").value("UNSUPPORTED_MEDIA_TYPE"));
    }

    @Test
    void rejectsDeclaredBodyAboveConfiguredLimit() throws Exception {
        String body = "x".repeat(513);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.schemaVersion").value("1"))
                .andExpect(jsonPath("$.error.code").value("PAYLOAD_TOO_LARGE"));
    }

    @Test
    void hidesUnexpectedExceptionDetails() throws Exception {
        when(userManagementService.login(any())).thenThrow(new IllegalStateException("sensitive implementation detail"));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@example.test","password":"A valid local passphrase 2026!"}
                                """))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.message").value("An unexpected error occurred."))
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"));
    }

    @Test
    void returnsSafeNotFoundForUnknownResource() throws Exception {
        mockMvc.perform(get("/does-not-exist"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("The requested resource was not found."));
    }
}

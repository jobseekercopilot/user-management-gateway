package com.jobseekercopilot.usermanagementgateway;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties =
        "authentication.service.token=test-only-authentication-service-token-32-bytes")
@AutoConfigureMockMvc
class OpenApiExportTest {
    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;

    @Test
    void exportOpenApi() throws Exception {
        String spec = mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/openapi.json"), spec);
    }

    @Test
    void contractExpressesCookieOwnershipAndVersionedErrorsWithoutTokenExposure() throws Exception {
        JsonNode root = objectMapper.readTree(mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());

        JsonNode session = root.at("/components/securitySchemes/browserSession");
        assertEquals("apiKey", session.path("type").asText());
        assertEquals("cookie", session.path("in").asText());
        assertEquals("__Host-jsc-access", session.path("name").asText());
        assertFalse(root.at("/components/securitySchemes").has("bearerAuth"));

        assertProtectedProfileOperation(root.at("/paths/~1api~1auth~1profile/get"),
                "200", "401", "404", "409", "429", "500", "503");
        assertProtectedProfileOperation(root.at("/paths/~1api~1auth~1profile/put"),
                "200", "400", "401", "403", "404", "409", "413", "415", "429", "500", "503");
        assertPublicOperation(root.at("/paths/~1api~1auth~1register/post"),
                "201", "400", "403", "409", "413", "415", "429", "500", "503");
        assertPublicOperation(root.at("/paths/~1api~1auth~1login/post"),
                "200", "400", "401", "403", "413", "415", "429", "500", "503");
        assertTrue(root.at("/paths/~1api~1auth~1csrf/get").isObject());
        assertTrue(root.at("/paths/~1api~1auth~1refresh/post/security").toString()
                .contains("browserRefresh"));
        assertTrue(root.at("/paths/~1api~1auth~1logout/post/security").toString()
                .contains("browserSession"));
        assertFalse(root.at("/components/schemas/User/properties").has("token"));

        assertTrue(root.at("/components/schemas/ApiError/properties/schemaVersion").isObject());
        assertTrue(root.at("/components/schemas/ApiError/properties/code").isObject());
        assertTrue(root.at("/components/schemas/FieldViolation/properties/field").isObject());

        for (String schema : List.of("RegisterRequest", "LoginRequest", "UserProfile",
                "Aspirations", "WorkPreferences", "PostcodeLocation", "Qualification", "Role")) {
            JsonNode additionalProperties = root.at("/components/schemas/" + schema
                    + "/additionalProperties");
            assertTrue(additionalProperties.isBoolean(), schema);
            assertFalse(additionalProperties.asBoolean(), schema);
        }
    }

    private void assertProtectedProfileOperation(JsonNode operation, String... responses) {
        assertTrue(operation.isObject());
        assertTrue(operation.path("security").toString().contains("browserSession"));
        assertFalse(operation.path("parameters").toString().contains("email"));
        assertFalse(operation.path("parameters").toString().contains("Authorization"));
        assertResponses(operation, responses);
    }

    private void assertPublicOperation(JsonNode operation, String... responses) {
        assertTrue(operation.isObject());
        assertTrue(operation.path("security").isMissingNode() || operation.path("security").isEmpty());
        assertResponses(operation, responses);
    }

    private void assertResponses(JsonNode operation, String... responseCodes) {
        for (String responseCode : responseCodes) {
            assertTrue(operation.path("responses").has(responseCode), responseCode);
            if (!responseCode.startsWith("2")) {
                assertTrue(operation.path("responses").path(responseCode).path("content").toString()
                        .contains("GatewayResponse"), responseCode + " must use the stable envelope");
            }
        }
    }
}

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

        assertEquals("3.1.0", root.at("/info/version").asText());
        JsonNode session = root.at("/components/securitySchemes/browserSession");
        assertEquals("apiKey", session.path("type").asText());
        assertEquals("cookie", session.path("in").asText());
        assertEquals("__Host-jsc-access", session.path("name").asText());
        JsonNode refresh = root.at("/components/securitySchemes/browserRefresh");
        assertEquals("apiKey", refresh.path("type").asText());
        assertEquals("cookie", refresh.path("in").asText());
        assertEquals("__Host-jsc-refresh", refresh.path("name").asText());
        assertFalse(root.at("/components/securitySchemes").has("bearerAuth"));

        assertProtectedProfileOperation(root.at("/paths/~1api~1auth~1profile/get"),
                "200", "401", "404", "409", "429", "500", "503");
        assertProtectedProfileOperation(root.at("/paths/~1api~1auth~1profile/put"),
                "200", "400", "401", "403", "404", "409", "413", "415", "429", "500", "503");
        assertTrue(root.at("/paths/~1api~1auth~1profile/patch/security").toString()
                .contains("browserSession"));
        JsonNode contactOperation = root.at(
                "/paths/~1api~1auth~1profile~1professional-contact/patch");
        assertProtectedProfileOperation(contactOperation,
                "200", "400", "401", "403", "409", "413", "415", "429", "500", "503");
        assertEquals("updateProfessionalContact", contactOperation.path("operationId").asText());
        assertEquals(
                "#/components/schemas/ProfessionalContact",
                contactOperation.at("/requestBody/content/application~1json/schema/$ref").asText());
        assertTrue(contactOperation.path("parameters").toString().contains("If-Match"));
        assertFalse(contactOperation.path("parameters").toString().contains("userId"));
        assertEquals(8, root.at(
                "/components/schemas/ProfessionalContact/properties/links/maxItems").asInt());
        assertEquals(40, root.at(
                "/components/schemas/ProfessionalContact/properties/phone/maxLength").asInt());
        assertEquals("^https://", root.at(
                "/components/schemas/ProfessionalLink/properties/url/pattern").asText());
        assertEquals(1, root.at(
                "/components/schemas/ProfessionalLink/properties/label/minLength").asInt());
        assertEquals(512, root.at(
                "/components/schemas/ProfessionalLink/properties/url/maxLength").asInt());
        assertTrue(root.at(
                "/components/schemas/UserProfile/properties/professionalContact/readOnly").asBoolean());
        assertTrue(root.at("/components/schemas/ProfessionalLink/required").toString()
                .contains("label"));
        assertTrue(root.at("/components/schemas/ProfessionalLink/required").toString()
                .contains("url"));
        assertTrue(root.at("/paths/~1api~1auth~1evidence/get/security").toString()
                .contains("browserSession"));
        assertTrue(root.at("/paths/~1api~1auth~1evidence/post/security").toString()
                .contains("browserSession"));
        assertTrue(root.at("/paths/~1api~1auth~1evidence~1{entryId}~1confirm/post").isObject());
        assertTrue(root.at("/components/schemas/EvidenceEntry/properties/revisions").isObject());
        assertTrue(root.at("/components/schemas/EvidenceWriteRequest/properties/category").isObject());
        assertEquals(2000, root.at(
                "/components/schemas/EvidenceWriteRequest/properties/description/maxLength")
                .asInt());
        assertEquals(2000, root.at(
                "/components/schemas/EvidenceWriteRequest/properties/responsibilities/maxLength")
                .asInt());
        assertEquals(2000, root.at(
                "/components/schemas/EvidenceWriteRequest/properties/achievements/maxLength")
                .asInt());
        assertEquals("object", root.at("/components/schemas/PartialDate/type").asText());
        assertEquals("string", root.at(
                "/components/schemas/EvidenceEntry/properties/supersededByEntryId/type/0").asText());
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
        assertStringConstraint(root, "RegisterRequest", "name", 1, 100, null, "Unicode code points");
        assertStringConstraint(root, "RegisterRequest", "email", 1, 254, "email", "Unicode code points");
        assertStringConstraint(root, "RegisterRequest", "password", 15, 128, "password", "exactly");
        assertStringConstraint(root, "LoginRequest", "email", 1, 254, "email", "Unicode code points");
        assertStringConstraint(root, "LoginRequest", "password", 1, 128, "password", "exactly");

        for (String schema : List.of("RegisterRequest", "LoginRequest", "UserProfile",
                "Aspirations", "WorkPreferences", "PostcodeLocation", "Qualification", "Role",
                "ProfessionalContact", "ProfessionalLink")) {
            JsonNode additionalProperties = root.at("/components/schemas/" + schema
                    + "/additionalProperties");
            assertTrue(additionalProperties.isBoolean(), schema);
            assertFalse(additionalProperties.asBoolean(), schema);
        }
    }

    private void assertStringConstraint(
            JsonNode root,
            String schema,
            String property,
            int minimum,
            int maximum,
            String format,
            String descriptionFragment) {
        JsonNode field = root.at("/components/schemas/" + schema + "/properties/" + property);
        assertEquals("string", field.path("type").asText(), schema + "." + property);
        assertEquals(minimum, field.path("minLength").asInt(), schema + "." + property);
        assertEquals(maximum, field.path("maxLength").asInt(), schema + "." + property);
        if (format != null) {
            assertEquals(format, field.path("format").asText(), schema + "." + property);
        }
        assertTrue(
                field.path("description").asText().contains(descriptionFragment),
                schema + "." + property + " description");
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

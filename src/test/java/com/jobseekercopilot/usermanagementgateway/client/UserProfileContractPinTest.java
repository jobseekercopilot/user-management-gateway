package com.jobseekercopilot.usermanagementgateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class UserProfileContractPinTest {

    private static final Path CONTRACT =
            Path.of("src/main/openapi/user-profile-service.yaml");
    private static final Path PIN =
            Path.of("src/main/openapi/user-profile-service.pin.json");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pinsExactReviewedProducerContractAndClientRelease() throws Exception {
        byte[] contractBytes = Files.readAllBytes(CONTRACT);
        JsonNode contract = objectMapper.readTree(contractBytes);
        JsonNode pin = objectMapper.readTree(Files.readString(PIN, StandardCharsets.UTF_8));

        assertEquals(1, pin.path("schemaVersion").asInt());
        assertEquals("jobseekercopilot/user-profile-service",
                pin.path("repository").asText());
        assertEquals("api/openapi.json", pin.path("contractPath").asText());
        assertEquals("2.3.0", pin.path("contractVersion").asText());
        assertEquals("a880add6e5c7106a2f3abec08a147823f88edf20",
                pin.path("sourceRevision").asText());
        assertEquals("3326ffc4c3f78fbd97325fe071d3848fdb3319ea997571f98cd792619905fc04",
                pin.path("sha256").asText());
        assertEquals(pin.path("sha256").asText(), sha256(contractBytes));
        assertEquals(pin.path("contractVersion").asText(),
                contract.path("info").path("version").asText());
        JsonNode locationId = contract.at(
                "/components/schemas/PostcodeLocation/properties/locationId");
        assertEquals("uuid", locationId.path("format").asText());
        assertFalse(locationId.has("maxLength"),
                "UUID schemas must not generate incompatible @Size validation");

        JsonNode client = pin.path("client");
        assertEquals("com.jobseekercopilot.clients", client.path("groupId").asText());
        assertEquals("user-profile-service-client", client.path("artifactId").asText());
        assertEquals("2.3.0-rev.a880add6e5c7", client.path("version").asText());
        assertEquals(
                "updateMyProfessionalContact",
                contract.at("/paths/~1api~1profiles~1me~1professional-contact/patch/operationId")
                        .asText());
        assertEquals(
                8,
                contract.at("/components/schemas/ProfessionalContact/properties/links/maxItems")
                        .asInt());
        assertEquals(
                "^https://",
                contract.at("/components/schemas/ProfessionalLink/properties/url/pattern")
                        .asText());
    }

    @Test
    void generatedInputPolicyMatchesProducerLongFormBounds() throws Exception {
        JsonNode contract = objectMapper.readTree(Files.readAllBytes(CONTRACT));
        JsonNode properties = contract.at(
                "/components/schemas/EvidenceWriteRequest/properties");

        assertEquals(2000, properties.path("description").path("maxLength").asInt());
        assertEquals(2000, properties.path("responsibilities").path("maxLength").asInt());
        assertEquals(2000, properties.path("achievements").path("maxLength").asInt());
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));
    }
}

package com.jobseekercopilot.usermanagementgateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;

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
        assertEquals("2.2.0", pin.path("contractVersion").asText());
        assertEquals("4e8c7c4c53bc89e97c76136d5633bbf1b93c3d55",
                pin.path("sourceRevision").asText());
        assertEquals("1e74f08ad044a144df2bafad3ef22d3b1ffde429dbdcc352cb5f1bdab5b87cc5",
                pin.path("sha256").asText());
        assertEquals(pin.path("sha256").asText(), sha256(contractBytes));
        assertEquals(pin.path("contractVersion").asText(),
                contract.path("info").path("version").asText());

        JsonNode client = pin.path("client");
        assertEquals("com.jobseekercopilot.clients", client.path("groupId").asText());
        assertEquals("user-profile-service-client", client.path("artifactId").asText());
        assertEquals("2.2.0-rev.4e8c7c4c53bc", client.path("version").asText());
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

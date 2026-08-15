package com.jobseekercopilot.usermanagementgateway.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import org.junit.jupiter.api.Test;

class AuthenticationContractPinTest {

    private static final Path CONTRACT =
            Path.of("src/main/openapi/authentication-service.yaml");
    private static final Path PIN =
            Path.of("src/main/openapi/authentication-service.pin.json");

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void pinsReviewedRegistrationConsentProducerAndConsumerContracts() throws Exception {
        byte[] contractBytes = Files.readAllBytes(CONTRACT);
        String contract = new String(contractBytes, StandardCharsets.UTF_8);
        JsonNode pin = objectMapper.readTree(Files.readString(PIN, StandardCharsets.UTF_8));

        assertEquals(1, pin.path("schemaVersion").asInt());
        assertEquals("jobseekercopilot/authentication-service",
                pin.path("repository").asText());
        assertEquals("target/openapi.json", pin.path("contractPath").asText());
        assertEquals("2.1.0", pin.path("contractVersion").asText());
        assertEquals("66dbbc1dffb1355c005d5c4336cabae2d2187808",
                pin.path("sourceRevision").asText());
        assertEquals("8ef5f12a32e836c2046fb163944b62d76cea31e389612408ca6ed1d1ccc42884",
                pin.path("sourceContractSha256").asText());
        assertEquals(pin.path("consumerContractSha256").asText(), sha256(contractBytes));

        assertTrue(contract.contains("version: 2.1.0"));
        assertTrue(contract.contains("x-source-repository: jobseekercopilot/authentication-service"));
        assertTrue(contract.contains("x-source-revision: " + pin.path("sourceRevision").asText()));
        assertTrue(contract.contains("x-source-contract-sha256: "
                + pin.path("sourceContractSha256").asText()));
        assertTrue(contract.contains("/api/auth/registration-requirements:"));
        assertTrue(contract.contains("operationId: getRegistrationLegalRequirements"));
        assertTrue(contract.contains("- termsAccepted"));
        assertTrue(contract.contains("- privacyNoticeAcknowledged"));
        assertTrue(contract.contains("- ageEligibilityConfirmed"));
        assertTrue(contract.contains("- legalVersion"));
        assertTrue(contract.contains("documents, payments]"));
        assertTrue(contract.contains("        payments:"));
    }

    private String sha256(byte[] content) throws Exception {
        return HexFormat.of().formatHex(
                MessageDigest.getInstance("SHA-256").digest(content));
    }
}

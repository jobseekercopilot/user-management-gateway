package com.jobseekercopilot.usermanagementgateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

@SpringBootTest(properties =
        "authentication.service.token=test-only-authentication-service-token-32-bytes")
class DocumentationContractTest {
    private static final Set<String> DOCUMENTED_ROUTES = Set.of(
            "GET /api/auth/csrf",
            "GET /api/auth/profile",
            "POST /api/auth/login",
            "POST /api/auth/logout",
            "POST /api/auth/refresh",
            "POST /api/auth/register",
            "PUT /api/auth/profile");

    private static final Map<String, String> CONFIGURATION = Map.ofEntries(
            Map.entry("APP_LOG_LEVEL", "${APP_LOG_LEVEL"),
            Map.entry("AUTHENTICATION_SERVICE_TOKEN", "${AUTHENTICATION_SERVICE_TOKEN"),
            Map.entry("AUTHENTICATION_SERVICE_URL", "${AUTHENTICATION_SERVICE_URL"),
            Map.entry("DOWNSTREAM_BULKHEAD_MAX_CONCURRENT", "${DOWNSTREAM_BULKHEAD_MAX_CONCURRENT"),
            Map.entry("DOWNSTREAM_CIRCUIT_FAILURE_THRESHOLD", "${DOWNSTREAM_CIRCUIT_FAILURE_THRESHOLD"),
            Map.entry("DOWNSTREAM_CIRCUIT_OPEN_DURATION_MS", "${DOWNSTREAM_CIRCUIT_OPEN_DURATION_MS"),
            Map.entry("DOWNSTREAM_CONNECT_TIMEOUT_MS", "${DOWNSTREAM_CONNECT_TIMEOUT_MS"),
            Map.entry("DOWNSTREAM_READ_TIMEOUT_MS", "${DOWNSTREAM_READ_TIMEOUT_MS"),
            Map.entry("DOWNSTREAM_RETRY_MAX_ATTEMPTS", "${DOWNSTREAM_RETRY_MAX_ATTEMPTS"),
            Map.entry("GATEWAY_ACCESS_COOKIE_NAME", "${GATEWAY_ACCESS_COOKIE_NAME"),
            Map.entry("GATEWAY_ACCESS_MAXIMUM_SECONDS", "${GATEWAY_ACCESS_MAXIMUM_SECONDS"),
            Map.entry("GATEWAY_ALLOWED_ORIGINS", "${GATEWAY_ALLOWED_ORIGINS"),
            Map.entry("GATEWAY_AUTH_RATE_GLOBAL_MAXIMUM", "${GATEWAY_AUTH_RATE_GLOBAL_MAXIMUM"),
            Map.entry("GATEWAY_AUTH_RATE_MAXIMUM", "${GATEWAY_AUTH_RATE_MAXIMUM"),
            Map.entry("GATEWAY_AUTH_RATE_MAXIMUM_CLIENTS", "${GATEWAY_AUTH_RATE_MAXIMUM_CLIENTS"),
            Map.entry("GATEWAY_AUTH_RATE_WINDOW_SECONDS", "${GATEWAY_AUTH_RATE_WINDOW_SECONDS"),
            Map.entry("GATEWAY_CSRF_COOKIE_NAME", "${GATEWAY_CSRF_COOKIE_NAME"),
            Map.entry("GATEWAY_REFRESH_CONCURRENCY_MAXIMUM", "${GATEWAY_REFRESH_CONCURRENCY_MAXIMUM"),
            Map.entry("GATEWAY_REFRESH_CONCURRENCY_SECONDS", "${GATEWAY_REFRESH_CONCURRENCY_SECONDS"),
            Map.entry("GATEWAY_REFRESH_COOKIE_NAME", "${GATEWAY_REFRESH_COOKIE_NAME"),
            Map.entry("GATEWAY_REFRESH_MAXIMUM_SECONDS", "${GATEWAY_REFRESH_MAXIMUM_SECONDS"),
            Map.entry("GATEWAY_REQUEST_MAXIMUM_BODY_BYTES", "${GATEWAY_REQUEST_MAXIMUM_BODY_BYTES"),
            Map.entry("GATEWAY_SECURE_COOKIES", "${GATEWAY_SECURE_COOKIES"),
            Map.entry("SERVER_PORT", "server.port=8083"),
            Map.entry("USER_PROFILE_SERVICE_URL", "${USER_PROFILE_SERVICE_URL"));

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping mappings;

    @Test
    void publicControllerRoutesMatchTheApiReference() throws IOException {
        Set<String> implemented = new TreeSet<>();
        for (var entry : mappings.getHandlerMethods().entrySet()) {
            if (!entry.getValue().getBeanType().getName().endsWith("UserManagementController")) {
                continue;
            }
            RequestMappingInfo info = entry.getKey();
            for (String path : info.getPatternValues()) {
                for (RequestMethod method : info.getMethodsCondition().getMethods()) {
                    implemented.add(method.name() + " " + path);
                }
            }
        }

        assertEquals(DOCUMENTED_ROUTES, implemented);
        String readme = read("README.md");
        String api = read("docs/API.md");
        DOCUMENTED_ROUTES.forEach(route -> {
            String[] parts = route.split(" ", 2);
            assertTrue(readme.contains("`" + parts[0] + "`") && readme.contains("`" + parts[1] + "`"), route);
            assertTrue(api.contains("`" + route + "`"), route);
        });
    }

    @Test
    void operationsReferenceCoversEveryRuntimeVariableAndAvoidsStaleClaims() throws IOException {
        String properties = read("src/main/resources/application.properties");
        String operations = read("docs/OPERATIONS.md");
        String publishedDocs = read("README.md") + read("docs/API.md") + operations;

        CONFIGURATION.forEach((variable, propertyDeclaration) -> {
            assertTrue(properties.contains(propertyDeclaration), variable + " missing from properties");
            assertTrue(operations.contains("`" + variable + "`"), variable + " missing from operations guide");
        });
        assertFalse(publishedDocs.toLowerCase().contains("wiremock"));
        assertFalse(publishedDocs.toLowerCase().contains("comprehensive coverage"));
        assertTrue(publishedDocs.contains("complete JWT"));
        assertTrue(publishedDocs.contains("password"));
    }

    private String read(String relativePath) throws IOException {
        return Files.readString(Path.of(System.getProperty("user.dir"), relativePath));
    }
}

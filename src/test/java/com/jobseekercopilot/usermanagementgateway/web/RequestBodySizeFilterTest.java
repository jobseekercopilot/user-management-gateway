package com.jobseekercopilot.usermanagementgateway.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RequestBodySizeFilterTest {

    @Test
    void rejectsNonPositiveMaximumAtStartup() {
        ObjectMapper objectMapper = new ObjectMapper();
        assertThrows(IllegalArgumentException.class, () -> new RequestBodySizeFilter(0, objectMapper));
        assertThrows(IllegalArgumentException.class, () -> new RequestBodySizeFilter(-1, objectMapper));
    }

    @Test
    void acceptsPositiveMaximumAtStartup() {
        assertDoesNotThrow(() -> new RequestBodySizeFilter(65_536, new ObjectMapper()));
    }

    @Test
    void rejectsDeclaredBodyAboveMaximumWithStableRedactedResponse() throws Exception {
        RequestBodySizeFilter filter = new RequestBodySizeFilter(8, new ObjectMapper());
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/auth/login");
        request.setContent("more-than-eight-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(413, response.getStatus());
        assertTrue(response.getContentAsString().contains("PAYLOAD_TOO_LARGE"));
        assertTrue(response.getContentAsString().contains("schemaVersion"));
        assertTrue(!response.getContentAsString().contains("more-than-eight"));
    }
}

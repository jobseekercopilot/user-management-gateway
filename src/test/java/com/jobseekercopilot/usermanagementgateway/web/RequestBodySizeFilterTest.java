package com.jobseekercopilot.usermanagementgateway.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

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
}

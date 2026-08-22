package com.jobseekercopilot.usermanagementgateway.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.mock.http.client.MockClientHttpResponse;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdPropagationTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter("user-management-gateway");
    private final ClientHttpRequestInterceptor interceptor =
            new CorrelationIdHttpClientConfig().correlationIdInterceptor();

    @Test
    void propagatesSafeInboundCorrelationIdToResponseAndDownstream() throws Exception {
        var request = requestWithCorrelationId("safe-correlation-123");
        var response = new MockHttpServletResponse();
        var downstream = new AtomicReference<String>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                downstream.set(interceptedCorrelationId()));

        assertEquals(
                List.of("safe-correlation-123"),
                response.getHeaders(CorrelationIdFilter.HEADER_NAME));
        assertEquals("safe-correlation-123", response.getHeader(CorrelationIdFilter.HEADER_NAME));
        assertEquals("safe-correlation-123", downstream.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    @Test
    void replacesUnsafeInboundCorrelationIdBeforePropagation() throws Exception {
        String unsafe = "unsafe correlation id";
        var request = requestWithCorrelationId(unsafe);
        var response = new MockHttpServletResponse();
        var downstream = new AtomicReference<String>();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
                downstream.set(interceptedCorrelationId()));

        String replacement = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertNotEquals(unsafe, replacement);
        assertEquals(replacement, downstream.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY));
    }

    private MockHttpServletRequest requestWithCorrelationId(String correlationId) {
        var request = new MockHttpServletRequest("GET", "/api/auth/profile");
        request.addHeader(CorrelationIdFilter.HEADER_NAME, correlationId);
        return request;
    }

    private String interceptedCorrelationId() throws IOException {
        HttpRequest request = mock(HttpRequest.class);
        HttpHeaders headers = new HttpHeaders();
        when(request.getHeaders()).thenReturn(headers);
        interceptor.intercept(request, new byte[0], (ignoredRequest, ignoredBody) ->
                new MockClientHttpResponse(new byte[0], HttpStatus.OK));
        return headers.getFirst(CorrelationIdFilter.HEADER_NAME);
    }
}

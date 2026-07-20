package com.jobseekercopilot.usermanagementgateway.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Set;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class RequestBodySizeFilter extends OncePerRequestFilter {

    private static final Set<String> BODY_METHODS = Set.of("POST", "PUT", "PATCH");
    private final long maximumBytes;
    private final ObjectMapper objectMapper;

    public RequestBodySizeFilter(
            @Value("${gateway.request.maximum-body-bytes:65536}") long maximumBytes,
            ObjectMapper objectMapper) {
        if (maximumBytes < 1) {
            throw new IllegalArgumentException("gateway.request.maximum-body-bytes must be positive");
        }
        this.maximumBytes = maximumBytes;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !BODY_METHODS.contains(request.getMethod());
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (request.getContentLengthLong() > maximumBytes) {
            writeTooLarge(response);
            return;
        }
        chain.doFilter(new BoundedRequest(request, maximumBytes), response);
    }

    private void writeTooLarge(HttpServletResponse response) throws IOException {
        response.setStatus(HttpServletResponse.SC_REQUEST_ENTITY_TOO_LARGE);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                GatewayResponse.failure(413, "PAYLOAD_TOO_LARGE", "Request body is too large."));
    }

    private static final class BoundedRequest extends HttpServletRequestWrapper {
        private final long maximumBytes;

        private BoundedRequest(HttpServletRequest request, long maximumBytes) {
            super(request);
            this.maximumBytes = maximumBytes;
        }

        @Override
        public ServletInputStream getInputStream() throws IOException {
            return new LimitedServletInputStream(super.getInputStream(), maximumBytes);
        }

        @Override
        public BufferedReader getReader() throws IOException {
            String encoding = getCharacterEncoding();
            Charset charset = encoding == null ? StandardCharsets.UTF_8 : Charset.forName(encoding);
            return new BufferedReader(new InputStreamReader(getInputStream(), charset));
        }
    }

    private static final class LimitedServletInputStream extends ServletInputStream {
        private final ServletInputStream delegate;
        private final long maximumBytes;
        private long bytesRead;

        private LimitedServletInputStream(ServletInputStream delegate, long maximumBytes) {
            this.delegate = delegate;
            this.maximumBytes = maximumBytes;
        }

        @Override
        public int read() throws IOException {
            int value = delegate.read();
            if (value >= 0) {
                count(1);
            }
            return value;
        }

        @Override
        public int read(byte[] bytes, int offset, int length) throws IOException {
            int read = delegate.read(bytes, offset, length);
            if (read > 0) {
                count(read);
            }
            return read;
        }

        private void count(int count) throws PayloadTooLargeIOException {
            bytesRead += count;
            if (bytesRead > maximumBytes) {
                throw new PayloadTooLargeIOException();
            }
        }

        @Override public boolean isFinished() { return delegate.isFinished(); }
        @Override public boolean isReady() { return delegate.isReady(); }
        @Override public void setReadListener(ReadListener listener) { delegate.setReadListener(listener); }
    }
}

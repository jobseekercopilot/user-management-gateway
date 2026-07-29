package com.jobseekercopilot.usermanagementgateway.security;

import com.jobseekercopilot.usermanagementgateway.model.GatewayResponse;
import com.jobseekercopilot.usermanagementgateway.service.UserManagementService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.DefaultOAuth2AuthenticatedPrincipal;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.server.resource.introspection.OpaqueTokenIntrospector;
import org.springframework.security.oauth2.server.resource.introspection.OAuth2IntrospectionException;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
public class GatewaySecurityConfig {

    @Bean
    SecurityFilterChain gatewaySecurityFilterChain(
            HttpSecurity http,
            GatewaySecurityProperties properties,
            GatewaySecurityErrorWriter errors,
            CsrfTokenRepository csrf,
            BearerTokenResolver browserCookieBearerTokenResolver,
            OpaqueTokenIntrospector browserSessionIntrospector) throws Exception {
        return http
                .httpBasic(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .csrf(config -> config
                        .csrfTokenRepository(csrf)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'self'"))
                        .frameOptions(frame -> frame.deny()))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/auth/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login",
                                "/api/auth/refresh", "/api/auth/logout",
                                "/api/auth/password-reset/request", "/api/auth/password-reset/complete").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers(
                                "/api/auth/profile", "/api/auth/profile/**",
                                "/api/auth/evidence", "/api/auth/evidence/**").authenticated()
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resource -> resource
                        .bearerTokenResolver(browserCookieBearerTokenResolver)
                        .opaqueToken(opaque -> opaque.introspector(browserSessionIntrospector))
                        .authenticationEntryPoint(errors::authenticationRequired)
                        .accessDeniedHandler(errors::accessDenied))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(errors::authenticationRequired)
                        .accessDeniedHandler(errors::accessDenied))
                .addFilterBefore(new SessionAuthenticationFailureFilter(errors),
                        BearerTokenAuthenticationFilter.class)
                .build();
    }

    @Bean
    CookieCsrfTokenRepository browserCsrfTokenRepository(GatewaySecurityProperties properties) {
        CookieCsrfTokenRepository csrf = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrf.setCookieName(properties.getSession().getCsrfCookieName());
        csrf.setHeaderName("X-CSRF-Token");
        csrf.setCookieCustomizer(cookie -> cookie
                .path("/")
                .secure(properties.getSession().isSecureCookies())
                .sameSite("Lax"));
        return csrf;
    }

    @Bean
    BearerTokenResolver browserCookieBearerTokenResolver(SessionCookieService cookies) {
        return request -> (request.getRequestURI().startsWith("/api/auth/profile")
                || request.getRequestURI().startsWith("/api/auth/evidence"))
                ? cookies.accessToken(request) : null;
    }

    @Bean
    OpaqueTokenIntrospector browserSessionIntrospector(UserManagementService service) {
        return token -> {
            try {
                var account = service.authenticate(token);
                return new DefaultOAuth2AuthenticatedPrincipal(
                        account.getId(),
                        Map.of("sub", account.getId(), "name", account.getName(), "email", account.getEmail()),
                        List.of(new SimpleGrantedAuthority("ROLE_USER")));
            } catch (HttpClientErrorException exception) {
                throw new InvalidBearerTokenException("Browser session is invalid");
            } catch (ResourceAccessException | HttpServerErrorException exception) {
                throw new OAuth2IntrospectionException("Authentication service is unavailable", exception);
            } catch (RuntimeException exception) {
                throw new OAuth2IntrospectionException("Browser session authentication failed", exception);
            }
        };
    }

    @Bean
    CorsConfigurationSource corsConfigurationSource(GatewaySecurityProperties properties) {
        CorsConfiguration cors = new CorsConfiguration();
        cors.setAllowedOrigins(properties.getSecurity().origins());
        cors.setAllowCredentials(true);
        cors.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "OPTIONS"));
        cors.setAllowedHeaders(List.of(
                "Content-Type", "X-CSRF-Token", "X-Correlation-Id", HttpHeaders.IF_MATCH));
        cors.setExposedHeaders(List.of(
                "X-Correlation-Id", HttpHeaders.RETRY_AFTER, HttpHeaders.ETAG));
        cors.setMaxAge(600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cors);
        return source;
    }

    @Bean
    GatewaySecurityErrorWriter gatewaySecurityErrorWriter(ObjectMapper objectMapper) {
        return new GatewaySecurityErrorWriter(objectMapper);
    }

    static final class GatewaySecurityErrorWriter {
        private final ObjectMapper objectMapper;

        GatewaySecurityErrorWriter(ObjectMapper objectMapper) {
            this.objectMapper = objectMapper;
        }

        void authenticationRequired(
                HttpServletRequest request, HttpServletResponse response, Exception exception) throws IOException {
            if (causedByIntrospectionFailure(exception)) {
                write(response, 503, "DEPENDENCY_UNAVAILABLE",
                        "A required service is temporarily unavailable.");
                return;
            }
            write(response, 401, "SESSION_REQUIRED", "The browser session is not authenticated.");
        }

        void accessDenied(
                HttpServletRequest request, HttpServletResponse response, Exception exception) throws IOException {
            write(response, 403, "REQUEST_FORBIDDEN", "The request is not permitted.");
        }

        void authenticationDependencyUnavailable(HttpServletResponse response) throws IOException {
            write(response, 503, "DEPENDENCY_UNAVAILABLE",
                    "A required service is temporarily unavailable.");
        }

        private void write(HttpServletResponse response, int status, String code, String message) throws IOException {
            response.setStatus(status);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getOutputStream(), GatewayResponse.failure(status, code, message));
        }

        private boolean causedByIntrospectionFailure(Throwable exception) {
            Throwable current = exception;
            while (current != null) {
                if (current instanceof OAuth2IntrospectionException) {
                    return true;
                }
                current = current.getCause();
            }
            return false;
        }
    }

    private static final class SessionAuthenticationFailureFilter extends OncePerRequestFilter {
        private final GatewaySecurityErrorWriter errors;

        private SessionAuthenticationFailureFilter(GatewaySecurityErrorWriter errors) {
            this.errors = errors;
        }

        @Override
        protected void doFilterInternal(
                HttpServletRequest request, HttpServletResponse response,
                jakarta.servlet.FilterChain chain) throws jakarta.servlet.ServletException, IOException {
            try {
                chain.doFilter(request, response);
            } catch (AuthenticationServiceException exception) {
                errors.authenticationDependencyUnavailable(response);
            }
        }
    }

}

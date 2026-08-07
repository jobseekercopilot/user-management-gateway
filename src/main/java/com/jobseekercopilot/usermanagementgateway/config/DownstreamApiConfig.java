package com.jobseekercopilot.usermanagementgateway.config;

import com.jobseekercopilot.generated.authenticationservice.api.AuthenticationApi;
import com.jobseekercopilot.generated.userprofileservice.api.EvidenceLibraryApi;
import com.jobseekercopilot.generated.userprofileservice.api.UserProfilesApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
public class DownstreamApiConfig {

    @Bean
    UserProfileAccessTokenContext userProfileAccessTokenContext() {
        return new UserProfileAccessTokenContext();
    }

    @Bean
    AuthenticationApi authenticationApi(
            @Value("${authentication.service.url}") String basePath,
            @Qualifier("correlationIdInterceptor") ClientHttpRequestInterceptor correlationIdInterceptor,
            AuthenticationServiceIdentityInterceptor serviceIdentityInterceptor,
            @Qualifier("authenticationResilienceInterceptor") DownstreamResilienceInterceptor resilienceInterceptor,
            @Value("${downstream.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${downstream.read-timeout-ms}") int readTimeoutMs) {
        RestTemplate restTemplate = restTemplate(
                correlationIdInterceptor, resilienceInterceptor, connectTimeoutMs, readTimeoutMs);
        restTemplate.getInterceptors().add(0, serviceIdentityInterceptor);
        var apiClient = new com.jobseekercopilot.generated.authenticationservice.client.ApiClient(restTemplate);
        apiClient.setBasePath(basePath);
        return new AuthenticationApi(apiClient);
    }

    @Bean
    UserProfilesApi userProfilesApi(
            @Value("${user.profile.service.url}") String basePath,
            @Qualifier("correlationIdInterceptor") ClientHttpRequestInterceptor correlationIdInterceptor,
            @Qualifier("userProfileResilienceInterceptor") DownstreamResilienceInterceptor resilienceInterceptor,
            UserProfileAccessTokenContext accessTokenContext,
            @Value("${downstream.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${downstream.read-timeout-ms}") int readTimeoutMs) {
        RestTemplate restTemplate = restTemplate(
                correlationIdInterceptor, resilienceInterceptor, connectTimeoutMs, readTimeoutMs);
        var apiClient = new com.jobseekercopilot.generated.userprofileservice.client.ApiClient(restTemplate);
        apiClient.setBasePath(basePath);
        apiClient.setBearerToken(accessTokenContext::currentToken);
        return new UserProfilesApi(apiClient);
    }

    @Bean
    EvidenceLibraryApi evidenceLibraryApi(
            @Value("${user.profile.service.url}") String basePath,
            @Qualifier("correlationIdInterceptor") ClientHttpRequestInterceptor correlationIdInterceptor,
            @Qualifier("userProfileResilienceInterceptor") DownstreamResilienceInterceptor resilienceInterceptor,
            UserProfileAccessTokenContext accessTokenContext,
            @Value("${downstream.connect-timeout-ms}") int connectTimeoutMs,
            @Value("${downstream.read-timeout-ms}") int readTimeoutMs) {
        RestTemplate restTemplate = restTemplate(
                correlationIdInterceptor, resilienceInterceptor, connectTimeoutMs, readTimeoutMs);
        var apiClient = new com.jobseekercopilot.generated.userprofileservice.client.ApiClient(restTemplate);
        apiClient.setBasePath(basePath);
        apiClient.setBearerToken(accessTokenContext::currentToken);
        return new EvidenceLibraryApi(apiClient);
    }

    @Bean
    DownstreamResilienceInterceptor authenticationResilienceInterceptor(
            @Value("${downstream.retry.max-attempts}") int maxAttempts,
            @Value("${downstream.circuit.failure-threshold}") int failureThreshold,
            @Value("${downstream.circuit.open-duration-ms}") long openDurationMs,
            @Value("${downstream.bulkhead.max-concurrent}") int maxConcurrent) {
        return new DownstreamResilienceInterceptor(
                "authentication-service", maxAttempts, failureThreshold, openDurationMs, maxConcurrent);
    }

    @Bean
    DownstreamResilienceInterceptor userProfileResilienceInterceptor(
            @Value("${downstream.retry.max-attempts}") int maxAttempts,
            @Value("${downstream.circuit.failure-threshold}") int failureThreshold,
            @Value("${downstream.circuit.open-duration-ms}") long openDurationMs,
            @Value("${downstream.bulkhead.max-concurrent}") int maxConcurrent) {
        return new DownstreamResilienceInterceptor(
                "user-profile-service", maxAttempts, failureThreshold, openDurationMs, maxConcurrent);
    }

    RestTemplate restTemplate(
            ClientHttpRequestInterceptor correlationIdInterceptor,
            DownstreamResilienceInterceptor resilienceInterceptor,
            int connectTimeoutMs,
            int readTimeoutMs) {
        if (connectTimeoutMs < 1 || readTimeoutMs < 1) {
            throw new IllegalArgumentException("Downstream timeouts must be positive");
        }
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(connectTimeoutMs))
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofMillis(readTimeoutMs));
        RestTemplate restTemplate = new RestTemplate(requestFactory);
        restTemplate.getInterceptors().add(correlationIdInterceptor);
        restTemplate.getInterceptors().add(resilienceInterceptor);
        return restTemplate;
    }
}

package com.jobseekercopilot.usermanagementgateway.logging;

import org.slf4j.MDC;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestTemplateCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.util.StringUtils;

@Configuration
public class CorrelationIdHttpClientConfig {

    @Bean
    RestTemplateCustomizer correlationIdRestTemplateCustomizer(
            ClientHttpRequestInterceptor correlationIdInterceptor) {
        return restTemplate -> restTemplate.getInterceptors().add(correlationIdInterceptor);
    }

    @Bean
    RestClientCustomizer correlationIdRestClientCustomizer(
            ClientHttpRequestInterceptor correlationIdInterceptor) {
        return restClientBuilder -> restClientBuilder.requestInterceptor(correlationIdInterceptor);
    }

    @Bean
    ClientHttpRequestInterceptor correlationIdInterceptor() {
        return (request, body, execution) -> {
            String correlationId = MDC.get(CorrelationIdFilter.MDC_KEY);
            if (StringUtils.hasText(correlationId)) {
                request.getHeaders().set(CorrelationIdFilter.HEADER_NAME, correlationId);
            }
            return execution.execute(request, body);
        };
    }
}

package com.jobseekercopilot.usermanagementgateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.media.IntegerSchema;
import io.swagger.v3.oas.models.media.ObjectSchema;
import io.swagger.v3.oas.models.media.StringSchema;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("Jobseeker Copilot - User Management Gateway API")
                        .description("Gateway API for user registration, authentication, progressive profile management and the Evidence Library. Orchestrates calls to authentication-service and user-profile-service.")
                        .version("4.0.0"))
                .components(new Components().addSecuritySchemes("browserSession",
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("__Host-jsc-access")
                                .description("HttpOnly browser session cookie set by the gateway; browser JavaScript cannot read it."))
                        .addSecuritySchemes("browserRefresh",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.APIKEY)
                                        .in(SecurityScheme.In.COOKIE)
                                        .name("__Host-jsc-refresh")
                                        .description("HttpOnly rotating refresh cookie set by the gateway.")))
                .tags(List.of(
                        new Tag().name("Authentication").description("User registration and login endpoints"),
                        new Tag().name("Profile").description("User profile retrieval and progressive update operations"),
                        new Tag().name("Evidence Library").description("Owner-scoped reusable evidence operations")
                ));
    }

    @Bean
    OpenApiCustomizer generatedPartialDateSchema() {
        return openApi -> openApi.getComponents().addSchemas(
                "PartialDate",
                new ObjectSchema()
                        .addProperty(
                                "precision",
                                new StringSchema()._enum(List.of("YEAR", "MONTH", "DAY")))
                        .addProperty(
                                "year",
                                new IntegerSchema().format("int32")
                                        .minimum(BigDecimal.valueOf(1900))
                                        .maximum(BigDecimal.valueOf(2200)))
                        .addProperty(
                                "month",
                                new IntegerSchema().format("int32")
                                        .minimum(BigDecimal.ONE)
                                        .maximum(BigDecimal.valueOf(12))
                                        .nullable(true))
                        .addProperty(
                                "day",
                                new IntegerSchema().format("int32")
                                        .minimum(BigDecimal.ONE)
                                        .maximum(BigDecimal.valueOf(31))
                                        .nullable(true))
                        .required(List.of("precision", "year")));
    }
}

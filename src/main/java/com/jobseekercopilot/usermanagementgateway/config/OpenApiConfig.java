package com.jobseekercopilot.usermanagementgateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.tags.Tag;
import java.util.List;
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
                        .version("2.1.0"))
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
}

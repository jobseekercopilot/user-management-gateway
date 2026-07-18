package com.jobseekercopilot.usermanagementgateway.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
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
                        .description("Gateway API for user registration, authentication, and profile management. Orchestrates calls to authentication-service and user-profile-service.")
                        .version("1.0.0"))
                .tags(List.of(
                        new Tag().name("Authentication").description("User registration and login endpoints"),
                        new Tag().name("Profile").description("User profile retrieval and update operations")
                ));
    }
}
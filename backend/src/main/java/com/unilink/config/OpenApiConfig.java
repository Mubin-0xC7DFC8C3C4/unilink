package com.unilink.config;
import io.swagger.v3.oas.models.OpenAPI; import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.*;
@Configuration
public class OpenApiConfig {
  @Bean public OpenAPI openAPI(){
    return new OpenAPI().info(new Info().title("UniLink API").version("1.0.0")
      .description("Verified student network for Bangladesh universities. Sessions are cookie-based (UL_SESSION); CSRF token via /api/csrf."));
  }
}

package com.portfolio.orders.service.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    OpenAPI orderServiceOpenApi() {
        return new OpenAPI().info(new Info()
                .title("Order Service API")
                .version("v1")
                .description("Places orders and publishes OrderCreated events to Kafka. Synthetic demo data only.")
                .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")));
    }
}

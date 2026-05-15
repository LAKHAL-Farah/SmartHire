package tn.esprit.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;

@Configuration
public class GatewayRoutesConfig {

    @Bean
    public RouteLocator msUserRoutes(RouteLocatorBuilder builder) {
        return builder.routes()
                .route("ms-user-auth", route -> route.path("/auth/**")
                        .uri("http://ms-user:8082"))
                .route("ms-user-users", route -> route.path("/api/v1/users/**")
                        .uri("http://ms-user:8082"))
                .build();
    }
}

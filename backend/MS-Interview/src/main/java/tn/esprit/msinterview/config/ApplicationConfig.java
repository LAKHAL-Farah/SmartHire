package tn.esprit.msinterview.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

@Configuration
public class ApplicationConfig {

    @Bean
    public ObjectMapper objectMapper() {
        return new ObjectMapper();
    }

    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .servers(List.of(
                        new Server()
                                .url("http://localhost:8081/interview-service")
                                .description("Local environment")
                ))
                .info(new Info()
                        .title("Interview Service API")
                        .description("Comprehensive interview management system with AI-powered evaluation and adaptive questioning engine")
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("SmartHire Team")
                                .url("https://smarthire.dev")
                        )
                );
    }

        @Bean
        public WebMvcConfigurer corsConfigurer() {
                return new WebMvcConfigurer() {
                        @Override
                        public void addCorsMappings(CorsRegistry registry) {
                                registry.addMapping("/api/**")
                                                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                                                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                                                .allowedHeaders("*")
                                                .maxAge(3600);

                                registry.addMapping("/audio/**")
                                                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                                                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                                                .allowedHeaders("*")
                                                .maxAge(3600);

                                registry.addMapping("/api/v1/audio/**")
                                                .allowedOriginPatterns("http://localhost:*", "http://127.0.0.1:*")
                                                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                                                .allowedHeaders("*")
                                                .maxAge(3600);
                        }
                };
        }
}

package tn.esprit.msuser.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS is handled by the API Gateway.
 * Do not add CORS mappings here to avoid duplicate headers.
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	@Override
	public void addCorsMappings(CorsRegistry registry) {
		// CORS is intentionally left to the API Gateway to avoid duplicate headers.
	}

}

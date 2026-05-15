package tn.esprit.msjob.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.nio.file.Path;
import java.nio.file.Paths;

@Configuration
public class ResumeStaticResourceConfig implements WebMvcConfigurer {

    private final String publicBaseUrl;
    private final Path storageDir;

    public ResumeStaticResourceConfig(
            @Value("${msjob.resume.public-base-url}") String publicBaseUrl,
            @Value("${msjob.resume.storage.path}") String storagePath
    ) {
        this.publicBaseUrl = normalizeUrlPath(publicBaseUrl);
        this.storageDir = Paths.get(storagePath).toAbsolutePath().normalize();
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // Serve local files via: {publicBaseUrl}/filename
        // Note: for production you usually want secured access instead of public static.
        registry.addResourceHandler(publicBaseUrl + "/**")
                .addResourceLocations(storageDir.toUri().toString());
    }

    private static String normalizeUrlPath(String path) {
        if (!StringUtils.hasText(path)) return "/uploads/resumes";
        String p = path.trim();
        if (!p.startsWith("/")) p = "/" + p;
        if (p.endsWith("/")) p = p.substring(0, p.length() - 1);
        return p;
    }
}


package tn.esprit.msassessment.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

/**
 * Protects {@code /api/v1/assessment/admin/**} with header {@code X-Admin-Api-Key}.
 */
@Component
@RequiredArgsConstructor
public class AssessmentAdminApiKeyFilter extends OncePerRequestFilter {

    @Value("${smarthire.assessment.admin-api-key:dev-assessment-admin}")
    private String expectedApiKey;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {

        String uri = request.getRequestURI();
        if (!uri.contains("/api/v1/assessment/admin")) {
            filterChain.doFilter(request, response);
            return;
        }

        // CORS preflight: browser sends OPTIONS without custom headers
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String key = request.getHeader("X-Admin-Api-Key");
        if (expectedApiKey == null || expectedApiKey.isBlank()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE, "Assessment admin API key not configured");
            return;
        }
        if (key == null || !key.equals(expectedApiKey)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or missing X-Admin-Api-Key");
            return;
        }

        UsernamePasswordAuthenticationToken auth = new UsernamePasswordAuthenticationToken(
                "assessment-admin",
                null,
                List.of(new SimpleGrantedAuthority("ROLE_ASSESSMENT_ADMIN"))
        );
        auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
        SecurityContextHolder.getContext().setAuthentication(auth);
        filterChain.doFilter(request, response);
    }
}

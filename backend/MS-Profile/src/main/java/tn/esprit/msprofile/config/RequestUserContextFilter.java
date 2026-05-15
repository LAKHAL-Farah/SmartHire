package tn.esprit.msprofile.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

@Component
public class RequestUserContextFilter extends OncePerRequestFilter {

    private static final String[] USER_ID_HEADERS = {"X-User-Id", "X-Profile-User-Id"};

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {
        UUID requestUserId = resolveUserId(request);
        if (requestUserId != null) {
            RequestUserContext.setCurrentUserId(requestUserId);
        }

        try {
            filterChain.doFilter(request, response);
        } finally {
            RequestUserContext.clear();
        }
    }

    private UUID resolveUserId(HttpServletRequest request) {
        for (String header : USER_ID_HEADERS) {
            UUID parsed = parseUuid(request.getHeader(header));
            if (parsed != null) {
                return parsed;
            }
        }

        return parseUuid(request.getParameter("userId"));
    }

    private UUID parseUuid(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            return null;
        }

        try {
            return UUID.fromString(rawValue.trim());
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }
}

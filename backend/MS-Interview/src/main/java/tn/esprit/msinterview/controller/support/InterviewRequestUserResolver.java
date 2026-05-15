package tn.esprit.msinterview.controller.support;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class InterviewRequestUserResolver {

    private static final String INTERVIEW_USER_HEADER = "X-Interview-User-Id";
    private static final String FALLBACK_USER_HEADER = "X-User-Id";

    public Long requireCurrentUserId(HttpServletRequest request) {
        String raw = firstNonBlank(
                request.getHeader(INTERVIEW_USER_HEADER),
                request.getHeader(FALLBACK_USER_HEADER)
        );

        if (raw == null) {
            throw new ResponseStatusException(
                    HttpStatus.UNAUTHORIZED,
                    "Missing authenticated interview user context."
            );
        }

        try {
            long parsed = Long.parseLong(raw.trim());
            if (parsed <= 0) {
                throw new NumberFormatException("User id must be positive");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid authenticated interview user id header."
            );
        }
    }

    public Long resolveAndAssertUserId(Long requestedUserId, HttpServletRequest request) {
        Long currentUserId = requireCurrentUserId(request);
        if (requestedUserId != null && !currentUserId.equals(requestedUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "User id does not match authenticated user.");
        }
        return currentUserId;
    }

    public void assertCurrentUserOwnsUserId(Long ownerUserId, HttpServletRequest request, String resourceLabel) {
        Long currentUserId = requireCurrentUserId(request);
        if (ownerUserId == null || !ownerUserId.equals(currentUserId)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, resourceLabel + " does not belong to authenticated user.");
        }
    }

    private static String firstNonBlank(String first, String second) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        return null;
    }
}

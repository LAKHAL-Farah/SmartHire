package tn.esprit.msroadmap.security;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;
import tn.esprit.msroadmap.Exception.BusinessException;

@Component
@RequiredArgsConstructor
public class CurrentUserIdResolver {

    private static final String INTERVIEW_USER_HEADER = "X-Interview-User-Id";
    private static final String USER_ID_HEADER = "X-User-Id";

    private final HttpServletRequest request;

    public Long resolveUserId(Long requestedUserId) {
        Long headerUserId = resolveHeaderUserId();

        if (headerUserId != null) {
            if (requestedUserId != null && !headerUserId.equals(requestedUserId)) {
                throw new AccessDeniedException("Requested user does not match authenticated user context");
            }
            return headerUserId;
        }

        if (requestedUserId == null || requestedUserId <= 0) {
            throw new BusinessException("userId must be a positive number");
        }

        return requestedUserId;
    }

    private Long resolveHeaderUserId() {
        Long interviewUserId = parsePositiveLong(request.getHeader(INTERVIEW_USER_HEADER));
        Long genericUserId = parsePositiveLong(request.getHeader(USER_ID_HEADER));

        if (interviewUserId != null && genericUserId != null && !interviewUserId.equals(genericUserId)) {
            throw new AccessDeniedException("Conflicting user identity headers");
        }

        return interviewUserId != null ? interviewUserId : genericUserId;
    }

    private Long parsePositiveLong(String raw) {
        if (raw == null) {
            return null;
        }

        String trimmed = raw.trim();
        if (trimmed.isEmpty() || !trimmed.matches("\\d+")) {
            return null;
        }

        try {
            long value = Long.parseLong(trimmed);
            return value > 0 ? value : null;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

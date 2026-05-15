package tn.esprit.msroadmap.Exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Used for Logic errors (e.g., Progress cannot be negative,
 * Roadmap already exists for this Career Path, etc.)
 */
@ResponseStatus(HttpStatus.BAD_REQUEST)
public class BusinessException extends RuntimeException {
    public BusinessException(String message) {
        super(message);
    }
}
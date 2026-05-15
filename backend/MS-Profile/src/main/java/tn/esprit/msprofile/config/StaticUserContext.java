package tn.esprit.msprofile.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class StaticUserContext {

    private final UUID staticUserId;

    public StaticUserContext(@Value("${app.static-user-id:00000000-0000-0000-0000-000000000001}") String staticUserId) {
        try {
            this.staticUserId = UUID.fromString(staticUserId);
        } catch (IllegalArgumentException ex) {
            throw new IllegalStateException("Invalid app.static-user-id value: " + staticUserId, ex);
        }
    }

    public UUID getCurrentUserId() {
        return staticUserId;
    }
}

package tn.esprit.msprofile.config;

import java.util.UUID;

public final class AppConstants {

    public static final UUID DEFAULT_USER_ID = UUID.fromString("00000000-0000-0000-0000-000000000001");

    @Deprecated(forRemoval = false)
    public static final UUID USER_ID = DEFAULT_USER_ID;

    public static UUID currentUserId() {
        return RequestUserContext.getCurrentUserId().orElse(DEFAULT_USER_ID);
    }

    private AppConstants() {
    }
}

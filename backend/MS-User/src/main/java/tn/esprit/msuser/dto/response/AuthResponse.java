package tn.esprit.msuser.dto.response;

import java.util.List;

public record AuthResponse(String Token, java.util.UUID UserId, String userName, String email, String roles) {
}

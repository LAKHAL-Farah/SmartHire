package tn.esprit.msuser.dto.response;

import lombok.*;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PasswordResetResponse {

    private Boolean success;
    private String message;
}

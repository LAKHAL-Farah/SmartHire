package tn.esprit.msuser.dto.request;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class UserProfileRequest {

    private UserRequest userRequest;
    private ProfileRequest profileRequest;
}

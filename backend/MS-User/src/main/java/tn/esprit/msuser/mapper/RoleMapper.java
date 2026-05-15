package tn.esprit.msuser.mapper;


import org.springframework.stereotype.Component;
import tn.esprit.msuser.dto.request.RoleRequest;
import tn.esprit.msuser.dto.response.RoleResponse;
import tn.esprit.msuser.entity.Role;

@Component
public class RoleMapper {

    public RoleResponse toResponse(Role role) {
        if (role == null) return null;

        return RoleResponse.builder()
                .id(role.getId())
                .name(role.getName())
                .userCount(role.getUsers() != null ? (long) role.getUsers().size() : 0L)
                .build();
    }

    public Role toEntity(RoleRequest request) {
        if (request == null) return null;

        return Role.builder()
                .name(request.getName())
                .build();
    }
}
package tn.esprit.msuser.service;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msuser.dto.request.RoleRequest;
import tn.esprit.msuser.dto.response.RoleResponse;
import tn.esprit.msuser.entity.Role;
import tn.esprit.msuser.entity.enumerated.RoleName;
import tn.esprit.msuser.exception.DuplicateResourceException;
import tn.esprit.msuser.exception.ResourceNotFoundException;
import tn.esprit.msuser.mapper.RoleMapper;
import tn.esprit.msuser.repository.RoleRepository;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class RoleService {

    private final RoleRepository roleRepository;
    private final RoleMapper roleMapper;

    @Transactional(readOnly = true)
    public List<RoleResponse> getAllRoles() {
        log.debug("Fetching all roles");
        return roleRepository.findAll().stream()
                .map(roleMapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RoleResponse getRoleById(UUID id) {
        log.debug("Fetching role by id: {}", id);
        Role role = findRoleById(id);
        return roleMapper.toResponse(role);
    }

    @Transactional(readOnly = true)
    public RoleResponse getRoleByName(RoleName name) {
        log.debug("Fetching role by name: {}", name);
        Role role = roleRepository.findByName(name)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with name: " + name));
        return roleMapper.toResponse(role);
    }

    public RoleResponse createRole(RoleRequest request) {
        log.debug("Creating new role with name: {}", request.getName());

        if (roleRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Role already exists with name: " + request.getName());
        }

        Role role = roleMapper.toEntity(request);
        Role savedRole = roleRepository.save(role);

        log.info("Role created successfully with id: {}", savedRole.getId());
        return roleMapper.toResponse(savedRole);
    }

    public RoleResponse updateRole(UUID id, RoleRequest request) {
        log.debug("Updating role with id: {}", id);

        Role role = findRoleById(id);

        if (role.getName() != request.getName() &&
                roleRepository.existsByName(request.getName())) {
            throw new DuplicateResourceException("Role already exists with name: " + request.getName());
        }

        role.setName(request.getName());
        Role updatedRole = roleRepository.save(role);

        log.info("Role updated successfully with id: {}", id);
        return roleMapper.toResponse(updatedRole);
    }

    public void deleteRole(UUID id) {
        log.debug("Deleting role with id: {}", id);

        Role role = findRoleById(id);

        if (role.getUsers() != null && !role.getUsers().isEmpty()) {
            throw new IllegalStateException("Cannot delete role with assigned users");
        }

        roleRepository.delete(role);
        log.info("Role deleted successfully with id: {}", id);
    }

    private Role findRoleById(UUID id) {
        return roleRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Role not found with id: " + id));
    }
}
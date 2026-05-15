package tn.esprit.msuser.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import tn.esprit.msuser.entity.enumerated.UserStatus;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "users")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class User implements UserDetails {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String passwordHash;



    @Enumerated(EnumType.STRING)
    private UserStatus status;

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;


    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;


    @OneToOne(mappedBy = "user", cascade = CascadeType.ALL)
    private UserProfile profile;


    @OneToMany(mappedBy = "user", cascade = CascadeType.ALL)
    private List<OAuthConnection> oauthConnections;

    /**
     * Face Recognition MFA fields
     */
    @Column(name = "face_recognition_enabled", nullable = false)
    @Builder.Default
    private Boolean faceRecognitionEnabled = false;

    /**
     * Face embedding identifier (reference ID stored separately for security)
     * NOT the raw embedding data, just an ID for future lookup
     * In production, this would point to a separate secure credential storage
     */
    @Column(name = "face_embedding_id", length = 500)
    private String faceEmbeddingId;

    @Column(name = "face_enabled_at")
    private LocalDateTime faceEnabledAt;

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + this.role.getName().name()));
    }
    @Override public String getPassword() { return this.passwordHash; }
    @Override public String getUsername() { return this.email; }
    @Override public boolean isAccountNonExpired() { return true; }
    @Override public boolean isAccountNonLocked() { return true; }
    @Override public boolean isCredentialsNonExpired() { return true; }
    @Override public boolean isEnabled() { return true; }
}
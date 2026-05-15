package tn.esprit.msuser.entity;

import jakarta.persistence.*;
import lombok.*;
import tn.esprit.msuser.entity.enumerated.AuthProvider;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "oauth_connections")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class OAuthConnection {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Enumerated(EnumType.STRING)
    private AuthProvider provider;

    private String providerUserId;

    private LocalDateTime connectedAt;
}
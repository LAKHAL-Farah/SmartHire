package tn.esprit.msuser.entity;

import jakarta.persistence.*;
import lombok.*;

import java.util.UUID;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserProfile {

    @Id
    private UUID userId;

    @MapsId
    @OneToOne
    @JoinColumn(name = "user_id")
    private User user;

    private String firstName;
    private String lastName;
    private String headline;
    private String location;
    private String githubUrl;
    private String linkedinUrl;
    private String avatarUrl;

    /** JSON: onboarding situation, career, answers, skillScores, completedAt, etc. */
    @Column(name = "onboarding_json", columnDefinition = "TEXT")
    private String onboardingJson;
}

package tn.esprit.msuser.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msuser.entity.Role;
import tn.esprit.msuser.entity.User;
import tn.esprit.msuser.entity.UserProfile;
import tn.esprit.msuser.entity.enumerated.RoleName;
import tn.esprit.msuser.entity.enumerated.UserStatus;
import tn.esprit.msuser.repository.RoleRepository;
import tn.esprit.msuser.repository.UserProfileRepository;
import tn.esprit.msuser.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class SmartHireUserInitializer implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserProfileRepository profileRepository;
    private final PasswordEncoder passwordEncoder;
        private final JdbcTemplate jdbcTemplate;

    @Override
    @Transactional
    public void run(String... args) {
                ensureRoleNameColumnSupportsAdmin();
        ensureRoles();
        seedUsers();
    }

        private void ensureRoleNameColumnSupportsAdmin() {
                try {
                        String dataType = jdbcTemplate.queryForObject(
                                        "SELECT DATA_TYPE FROM information_schema.columns " +
                                                        "WHERE table_schema = database() AND table_name = 'roles' AND column_name = 'name'",
                                        String.class
                        );

                        if (dataType == null || !"enum".equalsIgnoreCase(dataType)) {
                                return;
                        }

                        String columnType = jdbcTemplate.queryForObject(
                                        "SELECT COLUMN_TYPE FROM information_schema.columns " +
                                                        "WHERE table_schema = database() AND table_name = 'roles' AND column_name = 'name'",
                                        String.class
                        );

                        if (columnType == null || columnType.toLowerCase().contains("'admin'")) {
                                return;
                        }

                        List<String> values = new ArrayList<>();
                        Matcher matcher = Pattern.compile("'([^']*)'").matcher(columnType);
                        while (matcher.find()) {
                                values.add(matcher.group(1));
                        }

                        if (!values.contains("admin")) {
                                values.add(0, "admin");
                        }

                        if (values.isEmpty()) {
                                values = List.of("admin", "candidate", "recruiter");
                        }

                        String enumValues = values.stream()
                                        .distinct()
                                        .map(value -> "'" + value.replace("'", "''") + "'")
                                        .collect(Collectors.joining(","));

                        jdbcTemplate.execute("ALTER TABLE roles MODIFY COLUMN name ENUM(" + enumValues + ") NOT NULL");
                        log.info("Adjusted roles.name enum to include admin.");
                } catch (Exception ex) {
                        log.warn("Unable to verify/adjust roles.name column. Proceeding without change.", ex);
                }
        }

    private void ensureRoles() {
        for (RoleName roleName : RoleName.values()) {
            if (!roleRepository.existsByName(roleName)) {
                roleRepository.save(Role.builder().name(roleName).build());
                log.info("Seeded role: {}", roleName);
            }
        }
    }

    private void seedUsers() {
        List<SeedUser> users = List.of(
                new SeedUser(
                        "admin_smarthire",
                        "admin@smarthire.tn",
                        "Anis",
                        "Ben Salem",
                        "Admin",
                        "Tunis",
                        RoleName.admin,
                        "Admin@12345"
                ),
                new SeedUser(
                        "cloud_amin",
                        "amin.khalil@smarthire.tn",
                        "Amin",
                        "Khalil",
                        "Cloud Engineer",
                        "Tunis",
                        RoleName.candidate,
                        "Cloud@12345"
                ),
                new SeedUser(
                        "ai_mariem",
                        "mariem.benali@smarthire.tn",
                        "Mariem",
                        "Ben Ali",
                        "AI Engineer",
                        "Tunis",
                        RoleName.candidate,
                        "AI@12345"
                ),
                new SeedUser(
                        "dev_ons",
                        "ons.jemai@smarthire.tn",
                        "Ons",
                        "Jemai",
                        "Software Engineer",
                        "Tunis",
                        RoleName.candidate,
                        "Dev@12345"
                )
        );

        for (SeedUser seed : users) {
            upsertUser(seed);
        }
    }

    private void upsertUser(SeedUser seed) {
        Role role = roleRepository.findByName(seed.roleName)
                .orElseThrow(() -> new IllegalStateException("Missing role: " + seed.roleName));

        User user = userRepository.findByEmail(seed.email)
                .orElseGet(() -> User.builder().email(seed.email).createdAt(LocalDateTime.now()).build());

        user.setRole(role);
        user.setStatus(UserStatus.ACTIVE);
        user.setPasswordHash(passwordEncoder.encode(seed.password));
        user.setUpdatedAt(LocalDateTime.now());

        User savedUser = userRepository.save(user);

        UserProfile profile = profileRepository.findByUserId(savedUser.getId())
                .orElseGet(() -> UserProfile.builder().user(savedUser).build());

        profile.setFirstName(seed.firstName);
        profile.setLastName(seed.lastName);
        profile.setHeadline(seed.headline);
        profile.setLocation(seed.location);
        profile.setGithubUrl("https://github.com/" + seed.username);
        profile.setLinkedinUrl("https://linkedin.com/in/" + seed.username);
        profile.setAvatarUrl("https://ui-avatars.com/api/?name=" + seed.firstName + "+" + seed.lastName + "&size=200");

        profileRepository.save(profile);

        log.info("Seeded user: {} ({})", seed.email, seed.roleName);
    }

    private record SeedUser(
            String username,
            String email,
            String firstName,
            String lastName,
            String headline,
            String location,
            RoleName roleName,
            String password
    ) {}
}

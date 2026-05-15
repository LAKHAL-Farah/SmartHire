package tn.esprit.msuser.service;

import com.github.javafaker.Faker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msuser.entity.OAuthConnection;
import tn.esprit.msuser.entity.Role;
import tn.esprit.msuser.entity.User;
import tn.esprit.msuser.entity.UserProfile;
import tn.esprit.msuser.entity.enumerated.AuthProvider;
import tn.esprit.msuser.entity.enumerated.RoleName;
import tn.esprit.msuser.entity.enumerated.UserStatus;
import tn.esprit.msuser.repository.OAuthConnectionRepository;
import tn.esprit.msuser.repository.RoleRepository;
import tn.esprit.msuser.repository.UserProfileRepository;
import tn.esprit.msuser.repository.UserRepository;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.concurrent.TimeUnit;

@Component
@RequiredArgsConstructor
@Slf4j
public class DataGeneratorService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final UserProfileRepository profileRepository;
    private final OAuthConnectionRepository oauthConnectionRepository;
    private final PasswordEncoder passwordEncoder;
    private final Faker tunisianFaker;


    private static final List<String> TUNISIAN_FIRST_NAMES = Arrays.asList(
            "Mohamed", "Ahmed", "Salah", "Ali", "Hassen", "Karim", "Mehdi", "Omar", "Youssef", "Amine",
            "Fatma", "Soumaya", "Nour", "Imen", "Salma", "Amira", "Lina", "Mariem", "Safa", "Rania",
            "Khaled", "Walid", "Hichem", "Mounir", "Samir", "Lotfi", "Nabil", "Riadh", "Fethi", "Adel",
            "Leila", "Nadia", "Samia", "Mouna", "Sonia", "Rim", "Amel", "Dalila", "Naima", "Hayet"
    );

    private static final List<String> TUNISIAN_LAST_NAMES = Arrays.asList(
            "Ben Ali", "Ben Salah", "Ben Ahmed", "Trabelsi", "Mabrouk", "Hassan", "Karray", "Masmoudi",
            "Jaziri", "Gharbi", "Ayadi", "Bouazizi", "Jlassi", "Hammami", "Chaouch", "Dridi", "Baccar",
            "Ben Youssef", "Ben Ammar", "Hachicha", "Zghal", "Mejri", "Saidi", "Jemal", "Chakroun",
            "Elloumi", "Makni", "Bouaziz", "Chebbi", "Mansour", "Hadj", "Guesmi", "Mrad", "Ben Abdallah"
    );

    private static final List<String> TUNISIAN_CITIES = Arrays.asList(
            "Tunis", "Sfax", "Sousse", "Kairouan", "Bizerte", "Gabès", "Ariana", "Gafsa", "Monastir",
            "Ben Arous", "Kasserine", "Médenine", "Nabeul", "Tataouine", "Béja", "Jendouba", "Kef",
            "Mahdia", "Manouba", "Kébili", "Sidi Bouzid", "Siliana", "Zaghouan", "Tozeur"
    );

    private static final List<String> TUNISIAN_STREETS = Arrays.asList(
            "Rue Habib Bourguiba", "Avenue Habib Thameur", "Rue de Marseille", "Avenue de Paris",
            "Rue d'Alger", "Avenue Hédi Chaker", "Rue Mongi Slim", "Avenue de la Liberté",
            "Rue des Entrepreneurs", "Boulevard du 14 Janvier", "Rue Taieb Mhiri", "Avenue Mohamed V",
            "Rue Ibn Khaldoun", "Rue de Russie", "Avenue de Carthage", "Rue du Lac", "Rue de Grèce"
    );

    private static final List<String> TUNISIAN_COMPANIES = Arrays.asList(
            "Tunisie Telecom", "Orange Tunisie", "Ooredoo Tunisie", "Banque de Tunisie", "Amen Bank",
            "BIAT", "UBCI", "Attijari Bank", "STB", "BH Bank", "Poulina Group", "SFBT", "Délice",
            "Vitalait", "Carthage Cement", "One Tech", "SOTETEL", "ARTES", "ENNAKL Automobiles",
            "STIA", "Tunisair", "Carthage Airlines", "GIF", "SOMOCER", "Electrostar"
    );

    private static final List<String> TUNISIAN_JOB_TITLES = Arrays.asList(
            "Ingénieur Informatique", "Développeur Full Stack", "Chef de Projet IT", "Architecte Logiciel",
            "Data Scientist", "Administrateur Réseau", "Technicien Support", "Responsable Marketing",
            "Comptable", "Directeur Commercial", "Responsable RH", "Médecin", "Avocat", "Architecte",
            "Enseignant", "Journaliste", "Consultant", "Manager", "Analyste Financier"
    );

    private static final List<String> TUNISIAN_KEYWORDS = Arrays.asList(
            "Tunisie", "Carthage", "Méditerranée", "Innovation", "Digital", "Tech", "Startup",
            "Entrepreneuriat", "Développement", "Formation", "Emploi", "Stage", "Recrutement"
    );


    @Transactional
    public void test(String... args) throws Exception {
        log.info("Démarrage de la génération des données factices tunisiennes...");


        if (roleRepository.count() > 0 && userRepository.count() > 0) {
            log.info("Les données existent déjà. Génération ignorée.");
            return;
        }


        generateRoles();


        generateUsers(50);


        generateOAuthConnections();


        generateSpecificTunisianUsers();

        log.info("✅ Génération des données factices terminée avec succès!");
    }

    private void generateRoles() {
        log.info("👥 Génération des rôles...");


        for (RoleName roleName : RoleName.values()) {
            if (!roleRepository.existsByName(roleName)) {
                Role role = Role.builder()
                        .name(roleName)
                        .build();
                roleRepository.save(role);
                log.info("   ✓ Rôle créé: {}", roleName);
            }
        }
    }

    private void generateUsers(int count) {
        log.info("👤 Génération de {} utilisateurs tunisiens...", count);

        Role userRole = roleRepository.findByName(RoleName.candidate)
                .orElseThrow(() -> new RuntimeException("Rôle USER non trouvé"));
        Role adminRole = roleRepository.findByName(RoleName.recruiter)
                .orElseThrow(() -> new RuntimeException("Rôle ADMIN non trouvé"));


        List<Role> roles = Arrays.asList(userRole, adminRole);

        for (int i = 0; i < count; i++) {

            String firstName = getRandomTunisianFirstName();
            String lastName = getRandomTunisianLastName();
            String email = generateTunisianEmail(firstName, lastName);


            if (userRepository.existsByEmail(email)) {
                email = email.replace("@", i + "@");
            }


            Role role;
            double random = Math.random();
            if (random < 0.15) {
                role = adminRole;
            } else {
                role = userRole;
            }

            User user = User.builder()

                    .email(email)
                    .passwordHash(passwordEncoder.encode("Password123!"))
                    .status(getRandomUserStatus())
                    .role(role)
                    .createdAt(getRandomPastDate())
                    .updatedAt(LocalDateTime.now())
                    .build();

            User savedUser = userRepository.save(user);

            UserProfile profile = UserProfile.builder()
                    .user(savedUser)
                    .firstName(firstName)
                    .lastName(lastName)
                    .headline(generateTunisianHeadline())
                    .location(getRandomTunisianCity())
                    .githubUrl(generateGithubUrl(firstName, lastName))
                    .linkedinUrl(generateLinkedinUrl(firstName, lastName))
                    .avatarUrl(generateAvatarUrl(firstName, lastName))
                    .build();

            profileRepository.save(profile);

            log.debug("  Utilisateur créé: {} {} <{}>", firstName, lastName, email);
        }
    }

    private void generateSpecificTunisianUsers() {
        log.info("Génération d'utilisateurs tunisiens spécifiques...");

        createSpecificUser(
                "admin@tunisian-tech.tn",
                "Admin",
                "System",
                "Administrateur Système",
                "Tunis",
                RoleName.recruiter
        );



        createSpecificUser(
                "slim.gharbi@dev.tn",
                "Slim",
                "Gharbi",
                "Développeur Full Stack Senior",
                "Tunis",
                RoleName.candidate
        );

        createSpecificUser(
                "nour.jlassi@design.tn",
                "Nour",
                "Jlassi",
                "UX/UI Designer",
                "Sfax",
                RoleName.candidate
        );

        createSpecificUser(
                "mehdi.trabelsi@data.tn",
                "Mehdi",
                "Trabelsi",
                "Data Scientist",
                "Ariana",
                RoleName.candidate
        );

        createSpecificUser(
                "rania.bouaziz@pm.tn",
                "Rania",
                "Bouaziz",
                "Chef de Projet Digital",
                "Nabeul",
                RoleName.recruiter
        );
    }

    private void createSpecificUser(String email, String firstName, String lastName,
                                    String headline, String city, RoleName roleName) {
        if (!userRepository.existsByEmail(email)) {
            Role role = roleRepository.findByName(roleName)
                    .orElseThrow(() -> new RuntimeException("Rôle non trouvé: " + roleName));

            User user = User.builder()
                    .email(email)
                    .passwordHash(passwordEncoder.encode("Password123!"))
                    .status(UserStatus.ACTIVE)
                    .role(role)
                    .createdAt(LocalDateTime.now().minusMonths(6))
                    .updatedAt(LocalDateTime.now())
                    .build();

            User savedUser = userRepository.save(user);

            UserProfile profile = UserProfile.builder()
                    .user(savedUser)
                    .firstName(firstName)
                    .lastName(lastName)
                    .headline(headline)
                    .location(city)
                    .githubUrl("https://github.com/" + firstName.toLowerCase() + "." + lastName.toLowerCase())
                    .linkedinUrl("https://linkedin.com/in/" + firstName.toLowerCase() + "-" + lastName.toLowerCase())
                    .avatarUrl("https://ui-avatars.com/api/?name=" + firstName + "+" + lastName + "&size=200")
                    .build();

            profileRepository.save(profile);

            log.info("   ✓ Utilisateur spécifique créé: {} {}", firstName, lastName);
        }
    }

    private void generateOAuthConnections() {
        log.info("🔗 Génération des connexions OAuth...");

        List<User> users = userRepository.findAll();
        int oauthUsers = (int) (users.size() * 0.2); // 20% des utilisateurs

        Collections.shuffle(users);

        for (int i = 0; i < oauthUsers && i < users.size(); i++) {
            User user = users.get(i);
            AuthProvider provider = getRandomAuthProvider();

            if (oauthConnectionRepository.findByUserIdAndProvider(user.getId(), provider).isEmpty()) {
                OAuthConnection connection = OAuthConnection.builder()
                        .user(user)
                        .provider(provider)
                        .providerUserId(generateProviderUserId(provider, user))
                        .connectedAt(getRandomPastDate())
                        .build();

                oauthConnectionRepository.save(connection);
                log.debug("   ✓ Connexion {} créée pour {}", provider, user.getEmail());
            }
        }
    }


    private String getRandomTunisianFirstName() {
        return TUNISIAN_FIRST_NAMES.get(tunisianFaker.random().nextInt(TUNISIAN_FIRST_NAMES.size()));
    }

    private String getRandomTunisianLastName() {
        return TUNISIAN_LAST_NAMES.get(tunisianFaker.random().nextInt(TUNISIAN_LAST_NAMES.size()));
    }

    private String getRandomTunisianCity() {
        return TUNISIAN_CITIES.get(tunisianFaker.random().nextInt(TUNISIAN_CITIES.size()));
    }

    private String generateTunisianEmail(String firstName, String lastName) {
        String normalizedFirst = normalizeTunisianName(firstName);
        String normalizedLast = normalizeTunisianName(lastName);

        List<String> domains = Arrays.asList(
                "@gmail.com", "@yahoo.fr", "@hotmail.fr", "@tunisie.tn",
                "@topnet.tn", "@planet.tn", "@hexabyte.tn", "@tek-up.tn"
        );

        String domain = domains.get(tunisianFaker.random().nextInt(domains.size()));

        int format = tunisianFaker.random().nextInt(5);
        switch (format) {
            case 0:
                return normalizedFirst + "." + normalizedLast + domain;
            case 1:
                return normalizedFirst + normalizedLast + domain;
            case 2:
                return normalizedFirst + tunisianFaker.number().digits(2) + domain;
            case 3:
                return normalizedFirst.charAt(0) + "." + normalizedLast + domain;
            default:
                return normalizedFirst + "." + normalizedLast + tunisianFaker.number().digits(1) + domain;
        }
    }

    private String normalizeTunisianName(String name) {
        return name.toLowerCase()
                .replaceAll("\\s+", ".")
                .replaceAll("[éèêë]", "e")
                .replaceAll("[àâä]", "a")
                .replaceAll("[îï]", "i")
                .replaceAll("[ôö]", "o")
                .replaceAll("[ùûü]", "u");
    }

    private String generateTunisianHeadline() {
        String[] templates = {
                "%s passionné(e) par l'innovation en Tunisie",
                "%s basé(e) à %s avec %d ans d'expérience",
                "Expert %s dans l'écosystème tech tunisien",
                "Passionné(e) par le développement digital en Tunisie",
                "%s | Spécialiste en %s",
                "Tech Lead | %s | Tunisie",
                "Créateur de solutions innovantes pour le marché tunisien"
        };

        String jobTitle = TUNISIAN_JOB_TITLES.get(tunisianFaker.random().nextInt(TUNISIAN_JOB_TITLES.size()));
        String city = getRandomTunisianCity();
        String keyword = TUNISIAN_KEYWORDS.get(tunisianFaker.random().nextInt(TUNISIAN_KEYWORDS.size()));
        int experience = tunisianFaker.number().numberBetween(2, 15);

        String template = templates[tunisianFaker.random().nextInt(templates.length)];
        return String.format(template, jobTitle, city, experience, keyword);
    }

    private UserStatus getRandomUserStatus() {
        UserStatus[] statuses = UserStatus.values();
        double random = Math.random();

        // 90% ACTIVE, 5% INACTIVE, 3% SUSPENDED, 2% BANNED
        if (random < 0.90) {
            return UserStatus.ACTIVE;
        } else if (random < 0.95) {
            return UserStatus.INACTIVE;
        } else if (random < 0.98) {
            return UserStatus.SUSPENDED;
        } else {
            return UserStatus.BANNED;
        }
    }

    private AuthProvider getRandomAuthProvider() {
        AuthProvider[] providers = AuthProvider.values();
        return providers[tunisianFaker.random().nextInt(providers.length)];
    }

    private String generateGithubUrl(String firstName, String lastName) {
        String username = normalizeTunisianName(firstName + "-" + lastName);
        return "https://github.com/" + username + tunisianFaker.number().digits(2);
    }

    private String generateLinkedinUrl(String firstName, String lastName) {
        String username = normalizeTunisianName(firstName + "-" + lastName);
        return "https://linkedin.com/in/" + username;
    }

    private String generateAvatarUrl(String firstName, String lastName) {
        return "https://ui-avatars.com/api/?name=" + firstName + "+" + lastName +
                "&background=0D8F81&color=fff&size=200";
    }

    private String generateProviderUserId(AuthProvider provider, User user) {
        switch (provider) {
            case GOOGLE:
                return "google_" + UUID.randomUUID().toString();
            case GITHUB:
                return "github_" + tunisianFaker.number().digits(8);
            case FACEBOOK:
                return "fb_" + tunisianFaker.number().digits(10);
            default:
                return UUID.randomUUID().toString();
        }
    }

    private LocalDateTime getRandomPastDate() {
        Date pastDate = tunisianFaker.date().past(365, TimeUnit.DAYS);
        return pastDate.toInstant()
                .atZone(ZoneId.systemDefault())
                .toLocalDateTime();
    }

    @Transactional
    public void generateStartupTeam() {
        log.info("Génération d'une équipe startup tunisienne...");

        List<Map<String, String>> teamMembers = Arrays.asList(
                Map.of("role", "CEO", "name", "Ahmed", "lastName", "Ben Mahmoud"),
                Map.of("role", "CTO", "name", "Slim", "lastName", "Ayadi"),
                Map.of("role", "Lead Dev", "name", "Omar", "lastName", "Jaziri"),
                Map.of("role", "Marketing", "name", "Salma", "lastName", "Masmoudi"),
                Map.of("role", "UX Designer", "name", "Nour", "lastName", "Hammami"),
                Map.of("role", "DevOps", "name", "Karim", "lastName", "Trabelsi")
        );

        Role userRole = roleRepository.findByName(RoleName.candidate).orElseThrow();

        for (Map<String, String> member : teamMembers) {
            String firstName = member.get("name");
            String lastName = member.get("lastName");
            String role = member.get("role");
            String email = normalizeTunisianName(firstName) + "." +
                    normalizeTunisianName(lastName) + "@startup.tn";

            if (!userRepository.existsByEmail(email)) {
                User user = User.builder()
                        .email(email)
                        .passwordHash(passwordEncoder.encode("Password123!"))
                        .status(UserStatus.ACTIVE)
                        .role(userRole)
                        .createdAt(LocalDateTime.now().minusMonths(3))
                        .updatedAt(LocalDateTime.now())
                        .build();

                User savedUser = userRepository.save(user);

                UserProfile profile = UserProfile.builder()
                        .user(savedUser)
                        .firstName(firstName)
                        .lastName(lastName)
                        .headline(role + " @ Startup Tunisienne")
                        .location("Tunis, Tunisie")
                        .githubUrl(generateGithubUrl(firstName, lastName))
                        .linkedinUrl(generateLinkedinUrl(firstName, lastName))
                        .avatarUrl(generateAvatarUrl(firstName, lastName))
                        .build();

                profileRepository.save(profile);
                log.info("   ✓ Membre d'équipe créé: {} - {}", firstName + " " + lastName, role);
            }
        }
    }

    @Transactional
    public void generateUserWithSpecificProfile(String firstName, String lastName,
                                                String email, String headline,
                                                String city, RoleName roleName) {
        createSpecificUser(email, firstName, lastName, headline, city, roleName);
    }

    @Transactional(readOnly = true)
    public Map<String, Long> getStatistics() {
        Map<String, Long> stats = new HashMap<>();
        stats.put("totalUsers", userRepository.count());
        stats.put("totalProfiles", profileRepository.count());
        stats.put("totalOAuthConnections", oauthConnectionRepository.count());
        stats.put("totalRoles", roleRepository.count());

        stats.put("activeUsers", (long) userRepository.findByStatus(UserStatus.ACTIVE).size());

        return stats;
    }
}
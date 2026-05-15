package tn.esprit.msuser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tn.esprit.msuser.dto.request.ForgotPasswordRequest;
import tn.esprit.msuser.dto.request.ResetPasswordRequest;
import tn.esprit.msuser.dto.response.PasswordResetResponse;
import tn.esprit.msuser.entity.PasswordResetToken;
import tn.esprit.msuser.entity.User;
import tn.esprit.msuser.exception.ResourceNotFoundException;
import tn.esprit.msuser.repository.PasswordResetTokenRepository;
import tn.esprit.msuser.repository.UserRepository;

import java.time.LocalDateTime;
import java.util.Random;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional
public class PasswordResetService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final EmailService emailService;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.reset-password.expiration-hours:1}")
    private int expirationHours;

    @Value("${app.reset-password.code-length:6}")
    private int codeLength;

    /**
     * Génère un code de réinitialisation et l'envoie par email
     */
    public PasswordResetResponse requestPasswordReset(ForgotPasswordRequest request) {
        log.debug("Demande de réinitialisation pour l'email: {}", request.getEmail());

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new ResourceNotFoundException("Utilisateur non trouvé avec l'email: " + request.getEmail()));

        // Invalider les anciens tokens de cet utilisateur
        invalidateExistingTokens(user);

        // Générer un nouveau code
        String resetCode = generateResetCode();

        // Créer le token
        PasswordResetToken token = PasswordResetToken.builder()
                .tokenCode(resetCode)
                .user(user)
                .expirationTime(LocalDateTime.now().plusHours(expirationHours))
                .used(false)
                .build();

        passwordResetTokenRepository.save(token);
        log.info("Token de réinitialisation créé pour l'utilisateur: {}", user.getId());


        emailService.sendPasswordResetEmail(user, resetCode);

        return PasswordResetResponse.builder()
                .success(true)
                .message("Un code de réinitialisation a été envoyé à votre email")
                .build();
    }

    /**
     * Valide le code et change le mot de passe
     */
    public PasswordResetResponse resetPassword(ResetPasswordRequest request) {
        log.debug("Tentative de réinitialisation avec le code: {}", request.getResetCode());

        // Valider que les mots de passe correspondent
        if (!request.getNewPassword().equals(request.getConfirmPassword())) {
            return PasswordResetResponse.builder()
                    .success(false)
                    .message("Les mots de passe ne correspondent pas")
                    .build();
        }

        // Récupérer le token
        PasswordResetToken token = passwordResetTokenRepository.findByTokenCodeAndUsedFalse(request.getResetCode())
                .orElseThrow(() -> new ResourceNotFoundException("Code de réinitialisation invalide ou expiré"));

        // Vérifier un dernier fois que le token est valide (frais)
        if (!token.isValid()) {
            log.warn("Token expiré ou déjà utilisé: {}", request.getResetCode());
            throw new ResourceNotFoundException("Le code de réinitialisation a expiré");
        }


        User user = token.getUser();


        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setUpdatedAt(LocalDateTime.now());
        userRepository.save(user);

        // Marquer le token comme utilisé
        token.setUsed(true);
        token.setUpdatedAt(LocalDateTime.now());
        passwordResetTokenRepository.save(token);

        log.info("Mot de passe réinitialisé avec succès pour l'utilisateur: {}", user.getId());

        // Envoyer un email de confirmation
        emailService.sendPasswordChangedConfirmationEmail(user);

        return PasswordResetResponse.builder()
                .success(true)
                .message("Votre mot de passe a été réinitialisé avec succès")
                .build();
    }

    /**
     * Génère un code de réinitialisation aléatoire
     */
    private String generateResetCode() {
        Random random = new Random();
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < codeLength; i++) {
            code.append(random.nextInt(10));
        }
        return code.toString();
    }

    /**
     * Invalide les anciens tokens d'un utilisateur
     */
    private void invalidateExistingTokens(User user) {
        LocalDateTime now = LocalDateTime.now();
        passwordResetTokenRepository.deleteExpiredTokens(now);
    }
}

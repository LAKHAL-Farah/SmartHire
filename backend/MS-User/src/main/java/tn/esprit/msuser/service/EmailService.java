package tn.esprit.msuser.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import tn.esprit.msuser.entity.User;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailService {

    private final JavaMailSender mailSender;

    @Value("${spring.mail.from:noreply@smarthire.tn}")
    private String fromEmail;

    @Value("${app.reset-password.expiration-hours:1}")
    private int expirationHours;

    /**
     * Envoie un email avec le code de réinitialisation
     */
    public void sendPasswordResetEmail(User user, String resetCode) {
        try {
            String subject = "SmartHire - Code de réinitialisation de mot de passe";
            String text = buildPasswordResetEmailBody(user.getEmail(), resetCode);

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(user.getEmail());
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Email de réinitialisation envoyé avec succès à: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de l'email à {}: {}", user.getEmail(), e.getMessage());
            throw new RuntimeException("Erreur lors de l'envoi de l'email", e);
        }
    }

    /**
     * Construit le corps du message email
     */
    private String buildPasswordResetEmailBody(String email, String resetCode) {
        return "Bonjour,\n\n" +
                "Vous avez demandé la réinitialisation de votre mot de passe SmartHire.\n\n" +
                "Voici votre code de réinitialisation (valable " + expirationHours + " heure(s)):\n\n" +
                "    " + resetCode + "\n\n" +
                "Veuillez entrer ce code dans l'application pour réinitialiser votre mot de passe.\n\n" +
                "Si vous n'avez pas demandé cette réinitialisation, ignorez ce message.\n" +
                "Votre compte reste sécurisé.\n\n" +
                "Cordialement,\n" +
                "L'équipe SmartHire";
    }

    /**
     * Envoie un email de confirmation de changement de mot de passe
     */
    public void sendPasswordChangedConfirmationEmail(User user) {
        try {
            String subject = "SmartHire - Votre mot de passe a été changé";
            String text = "Bonjour,\n\n" +
                    "Votre mot de passe a été réinitialisé avec succès.\n\n" +
                    "Si vous n'avez pas effectué cette action, veuillez contacter le support immédiatement.\n\n" +
                    "Cordialement,\n" +
                    "L'équipe SmartHire";

            SimpleMailMessage message = new SimpleMailMessage();
            message.setFrom(fromEmail);
            message.setTo(user.getEmail());
            message.setSubject(subject);
            message.setText(text);

            mailSender.send(message);
            log.info("Email de confirmation envoyé à: {}", user.getEmail());

        } catch (Exception e) {
            log.error("Erreur lors de l'envoi de l'email de confirmation à {}: {}", user.getEmail(), e.getMessage());
        }
    }
}

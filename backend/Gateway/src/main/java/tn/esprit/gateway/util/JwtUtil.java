package tn.esprit.gateway.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.io.Decoders;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Component;
import java.security.Key;

/**
 * Utilitaire pour valider les tokens JWT.
 * 
 * IMPORTANT: La clé de signature doit être identique à celle utilisée dans MS-User.
 * Les deux services décodent le même token avec la même clé.
 * 
 * Clé partagée: 28e65c6afd4f3e61b9689a52ba0d8eb2528a0121686378028494a76f90900687
 */
@Component
public class JwtUtil {
    
    // ⚠️ DOIT ÊTRE IDENTIQUE À MS-USER
    private final String Enc_key = "28e65c6afd4f3e61b9689a52ba0d8eb2528a0121686378028494a76f90900687";

    /**
     * Valide un token JWT et lève une exception si invalide.
     * 
     * @param token Le token à valider
     * @throws io.jsonwebtoken.JwtException Si le token est invalide ou expiré
     */
    public void validateToken(final String token) {
        Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token);
    }

    /**
     * Valide un token JWT et retourne ses claims (données).
     * 
     * Claims contiennent:
     * - subject: email de l'utilisateur
     * - roles: liste des rôles ["ROLE_recruiter", "ROLE_candidate"]
     * - nom: prénom de l'utilisateur
     * - mail: email de l'utilisateur
     * - exp: timestamp d'expiration
     * 
     * @param token Le token à valider
     * @return Les claims du token
     * @throws io.jsonwebtoken.JwtException Si le token est invalide
     */
    public Claims validateTokenAndGetClaims(final String token) {
        return Jwts.parserBuilder()
                .setSigningKey(getSignKey())
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * Extrait l'email (subject) du token.
     * 
     * @param token Le token JWT
     * @return L'email de l'utilisateur
     */
    public String getEmailFromToken(String token) {
        return validateTokenAndGetClaims(token).getSubject();
    }

    /**
     * Génère la clé de signature à partir de la clé encodée en Base64.
     */
    private Key getSignKey() {
        byte[] keyBytes = Decoders.BASE64.decode(Enc_key);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}
package tn.esprit.msuser.service;

import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.io.Decoders;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import tn.esprit.msuser.dto.response.AuthResponse;
import tn.esprit.msuser.entity.User;

import java.security.Key;
import java.util.Date;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
public class JwtService {

    @Value("${key.secret}")
    private String enc_key;

    @Value("${face.verification.token.expires.seconds:45}")
    private int faceTokenExpirationSeconds;

    public JwtService(@Value("${key.secret}") String encKey) {
        this.enc_key = encKey;
    }

    public AuthResponse GenerateToken(User user) {
        final long current = System.currentTimeMillis();
        final long expire = current + 30 * 60 * 1000;

        List<String> roles = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();

        final Map<String, Object> claims = Map.of(
                "nom", user.getProfile().getFirstName(),
                "mail", user.getEmail(),
                "roles", roles
        );

        final String jws = Jwts.builder()
                .issuedAt(new Date(current))
                .expiration(new Date(expire))
                .subject(user.getEmail())
                .claims(claims)
                .signWith(getKey())
                .compact();

        return new AuthResponse(jws, user.getId(), (user.getProfile().getFirstName()+" "+user.getProfile().getLastName()), user.getEmail(), user.getRole().getName().name());
    }

    /**
     * Generate a temporary token for face verification (MFA step 2)
     * This token is short-lived (30-60 seconds) and can only be used with /auth/verify-face endpoint
     * 
     * Claims included:
     * - userId: User ID for verification
     * - email: User email for audit trail
     * - type: "FACE_VERIFICATION" (to prevent use with other endpoints)
     * - purpose: Limited to face verification only
     *
     * @param user The user to generate token for
     * @return Temporary JWT token for face verification
     */
    public String generateFaceVerificationToken(User user) {
        log.debug("Generating face verification temporary token for user: {}", user.getId());
        
        final long current = System.currentTimeMillis();
        // Short expiration for security (30-60 seconds from config)
        final long expire = current + (faceTokenExpirationSeconds * 1000L);

        final Map<String, Object> claims = Map.of(
                "userId", user.getId().toString(),
                "email", user.getEmail(),
                "type", "FACE_VERIFICATION",
                "purpose", "MFA face verification only"
        );

        final String jws = Jwts.builder()
                .issuedAt(new Date(current))
                .expiration(new Date(expire))
                .subject(user.getEmail())
                .claims(claims)
                .signWith(getKey())
                .compact();

        log.info("Face verification token generated for user: {}", user.getId());
        return jws;
    }

    private Key getKey() {
        final byte[] decoder = Decoders.BASE64.decode(enc_key);
        return Keys.hmacShaKeyFor(decoder);
    }
}
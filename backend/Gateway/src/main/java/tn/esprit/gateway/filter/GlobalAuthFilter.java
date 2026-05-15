package tn.esprit.gateway.filter;

import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import tn.esprit.gateway.util.JwtUtil;
import tn.esprit.gateway.config.RouteAuthorizationConfig;
import java.util.List;
import io.jsonwebtoken.Claims;

/**
 * Filtre d'authentification et d'autorisation global pour le Gateway.
 * 
 * Responsabilités:
 * 1. Valider les tokens JWT
 * 2. Extraire les rôles du token
 * 3. Vérifier les autorisations selon la configuration RouteAuthorizationConfig
 * 4. Laisser passer les routes publiques
 * 
 * FLUX:
 * Request → GlobalAuthFilter → Validation JWT → Extraction rôles → Vérification autorisations → Routing
 */
@Component
@RequiredArgsConstructor
public class GlobalAuthFilter implements GlobalFilter, Ordered {

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RouteAuthorizationConfig authConfig;

    /**
     * Filtre global appliqué à toutes les requêtes passant par le Gateway.
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String path = exchange.getRequest().getURI().getPath().toLowerCase();
        String method = exchange.getRequest().getMethod().toString();

        try {
            // 1. Route publique sans authentification requise ?
            if (isPublicRoute(path, method)) {
                return chain.filter(exchange);
            }

            // 2. Extraire et valider le token JWT
            String token = extractToken(exchange);

            // 3. Valider le token et récupérer les claims
            Claims claims = jwtUtil.validateTokenAndGetClaims(token);

            // 4. Extraire les rôles du token
            @SuppressWarnings("unchecked")
            List<String> roles = (List<String>) claims.get("roles");

            // 5. Vérifier l'autorisation selon la configuration centralisée
            checkAuthorization(path, method, roles);

        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Token invalide ou expiré");
        }

        return chain.filter(exchange);
    }

    /**
     * Vérifie si une route est publique (pas d'authentification requise).
     */
    private boolean isPublicRoute(String path, String method) {
        // Always allow CORS preflight requests (no JWT on browser preflight).
        if ("OPTIONS".equalsIgnoreCase(method)) {
            return true;
        }

        // Routes d'authentification
        if (path.contains("/auth/connexion")
                || path.contains("/auth/login-mfa")
                || path.contains("/auth/verify-face")
                || path.contains("/auth/forgot-password")
                || path.contains("/auth/reset-password")
                || path.contains("/ms-user/auth")) {
            return true;
        }

        // POST /api/v1/users - Inscription publique
        if (path.contains("/api/v1/users") && "POST".equalsIgnoreCase(method)) {
            return true;
        }

        return false;
    }

    /**
     * Extrait le token JWT du header Authorization.
     * Format attendu: "Bearer <token>"
     */
    private String extractToken(ServerWebExchange exchange) {
        List<String> authHeaders = exchange.getRequest()
                .getHeaders()
                .get(HttpHeaders.AUTHORIZATION);

        if (authHeaders == null || authHeaders.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Header Authorization manquant");
        }

        String authHeader = authHeaders.get(0);

        if (!authHeader.startsWith("Bearer ")) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Format token invalide. Attendu: 'Bearer <token>'");
        }

        return authHeader.substring(7); // Enlever "Bearer "
    }

    /**
     * Vérifie l'autorisation selon la configuration centralisée.
     * Utilise RouteAuthorizationConfig pour déterminer les rôles requis.
     */
    private void checkAuthorization(String path, String method, List<String> roles) {
        // Récupérer les rôles requis depuis la configuration
        List<String> requiredRoles = authConfig.getRequiredRoles(path, method);

        // Si requiredRoles est null, la route n'est pas configurée = public
        if (requiredRoles == null) {
            return;
        }

        // Vérifier que l'utilisateur a au moins l'un des rôles requis
        boolean hasRequiredRole = roles != null && roles.stream()
                .anyMatch(userRole -> requiredRoles.stream()
                        .anyMatch(requiredRole -> 
                            userRole.equalsIgnoreCase(requiredRole) ||
                            userRole.equalsIgnoreCase("ROLE_" + requiredRole)
                        )
                );

        if (!hasRequiredRole) {
            String rolesRequired = String.join(", ", requiredRoles);
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Accès refusé. Rôles requis: " + rolesRequired
            );
        }
    }

    @Override
    public int getOrder() {
        return -1; // Exécuté avant les autres filtres
    }
}
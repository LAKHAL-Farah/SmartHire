package tn.esprit.gateway.config;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * Configuration centralisée pour gérer les autorisations par route.
 * 
 * GUIDE POUR LES DÉVELOPPEURS:
 * ============================
 * 
 * Pour ajouter une nouvelle route avec contrôle de rôles:
 * 
 * 1. Ajouter la route dans initializeRouteRules()
 * 2. Spécifier les rôles requis pour chaque méthode HTTP
 * 
 * Exemple:
 *   routeRules.put("/api/v1/assessments", new RouteRule(
 *       "GET", Arrays.asList("recruiter", "admin"),   // GET nécessite recruiter ou admin
 *       "POST", Arrays.asList("admin")                 // POST nécessite admin
 *   ));
 */
@Component
public class RouteAuthorizationConfig {

    private Map<String, RouteRule> routeRules;

    public RouteAuthorizationConfig() {
        initializeRouteRules();
    }

    /**
     * Initialise les règles d'autorisation pour toutes les routes.
     * Format: "/path/api/v1/resource" -> RouteRule avec les rôles par méthode HTTP
     */
    private void initializeRouteRules() {
        routeRules = new HashMap<>();

        // ==================== MS-USER Routes ====================
        
        // GET /api/v1/users - Liste tous les utilisateurs (RECRUITER ONLY)
        // POST /api/v1/users - Inscription publique (PUBLIC)
        // PUT /api/v1/users/{id} - Mise à jour (RECRUITER)
        // DELETE /api/v1/users/{id} - Suppression (RECRUITER)
//        routeRules.put("/ms-user/api/v1/users", new RouteRule()
//            .addRule("GET", Arrays.asList("ROLE_recruiter", "ROLE_admin"))
//            .addRule("PUT", Arrays.asList("ROLE_recruiter", "ROLE_admin"))
//            .addRule("DELETE", Arrays.asList("ROLE_recruiter", "ROLE_admin"))
//            .addRule("PATCH", Arrays.asList("ROLE_recruiter", "ROLE_admin"))
//            // POST EST PUBLIC (pas dans les règles = public)
//        );

        routeRules.put("/ms-user/api/v1/roles", new RouteRule()
                        .addRule("GET", Arrays.asList("ROLE_recruiter", "ROLE_admin"))
                        .addRule("PUT", Arrays.asList("ROLE_recruiter", "ROLE_admin"))
                        .addRule("DELETE", Arrays.asList("ROLE_recruiter", "ROLE_admin"))
                        .addRule("PATCH", Arrays.asList("ROLE_recruiter", "ROLE_admin"))
                // POST EST PUBLIC (pas dans les règles = public)
        );

        // GET /api/v1/users/{id} - Récupérer un utilisateur par ID (AUTHENTICATED)
        // Ce pattern est géré différemment - utilisé pour les endpoints sans restriction de rôle
        // mais qui nécessitent une authentification

        // ==================== MS-ASSESSMENT Routes ====================
        // À remplir par les développeurs de MS-Assessment
        // Exemple:
        // routeRules.put("/api/v1/assessments", new RouteRule()
        //     .addRule("GET", Arrays.asList("recruiter"))
        //     .addRule("POST", Arrays.asList("admin"))
        // );

        // ==================== MS-ROADMAP Routes ====================
        // À remplir par les développeurs de MS-Roadmap
    }

    /**
     * Récupère les rôles requis pour une route et une méthode HTTP données.
     * 
     * @param path Le chemin de la route (ex: /api/v1/users)
     * @param method La méthode HTTP (GET, POST, PUT, DELETE, PATCH)
     * @return Liste des rôles autorisés, null si public ou non configuré
     */
    public List<String> getRequiredRoles(String path, String method) {
        RouteRule rule = findMatchingRule(path);
        
        if (rule == null) {
            return null; // Route non configurée = PUBLIC
        }

        return rule.getRolesForMethod(method);
    }

    /**
     * Trouve la règle correspondant à une route.
     * Supporte les patterns avec {id}, {uuid}, etc.
     */
    private RouteRule findMatchingRule(String path) {
        // Recherche directe
        if (routeRules.containsKey(path)) {
            return routeRules.get(path);
        }

        // Recherche avec pattern (ex: /api/v1/users/{id} matche /api/v1/users)
        for (String rulePath : routeRules.keySet()) {
            if (path.startsWith(rulePath)) {
                return routeRules.get(rulePath);
            }
        }

        return null;
    }

    /**
     * Classe interne pour représenter les règles d'une route.
     */
    public static class RouteRule {
        private Map<String, List<String>> methodRoles;

        public RouteRule() {
            this.methodRoles = new HashMap<>();
        }

        public RouteRule(String method, List<String> roles) {
            this();
            this.methodRoles.put(method, roles);
        }

        /**
         * Ajoute une règle pour une méthode HTTP spécifique.
         * 
         * @param method La méthode HTTP (GET, POST, PUT, DELETE, PATCH)
         * @param roles La liste des rôles autorisés
         * @return this (pour le chaînage)
         */
        public RouteRule addRule(String method, List<String> roles) {
            this.methodRoles.put(method.toUpperCase(), roles);
            return this;
        }

        /**
         * Récupère les rôles requis pour une méthode.
         * 
         * @param method La méthode HTTP
         * @return Liste des rôles, ou null si la méthode n'est pas restreinte (PUBLIC)
         */
        public List<String> getRolesForMethod(String method) {
            return this.methodRoles.get(method.toUpperCase());
        }
    }
}

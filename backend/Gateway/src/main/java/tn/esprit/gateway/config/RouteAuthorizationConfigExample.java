package tn.esprit.gateway.config;

import org.springframework.stereotype.Component;
import java.util.*;

/**
 * ✅ EXEMPLE DE CONFIGURATION POUR MS-ASSESSMENT
 * 
 * Cet exemple montre comment les développeurs de MS-Assessment et MS-Roadmap
 * peuvent configurer leurs routes et leur contrôle de rôles.
 * 
 * COPIER CET EXEMPLE dans RouteAuthorizationConfig.initializeRouteRules()
 */
public class RouteAuthorizationConfigExample {

    /**
     * Exemple 1: MS-ASSESSMENT Routes
     * 
     * Scénario:
     * - Les recruteurs créent et gèrent les assessments
     * - Les candidats peuvent voir et passer les tests
     * - Seul l'admin peut supprimer les tests
     */
    public static void exampleForMSAssessment() {
        // À ajouter dans initializeRouteRules():
        
        // routeRules.put("/api/v1/assessments", new RouteRule()
        //     .addRule("GET", Arrays.asList("recruiter", "candidate", "admin"))  // Tous peuvent voir
        //     .addRule("POST", Arrays.asList("recruiter", "admin"))              // Recruiter crée
        //     .addRule("PUT", Arrays.asList("recruiter", "admin"))               // Recruiter modifie
        //     .addRule("DELETE", Arrays.asList("admin"))                         // Seul admin supprime
        // );
        //
        // routeRules.put("/api/v1/assessments/mine", new RouteRule()
        //     .addRule("GET", Arrays.asList("candidate"))                        // Candidat voit ses tests
        // );
        //
        // routeRules.put("/api/v1/questions", new RouteRule()
        //     .addRule("GET", Arrays.asList("recruiter", "admin"))               // Recruiter gère les questions
        //     .addRule("POST", Arrays.asList("recruiter", "admin"))
        //     .addRule("PUT", Arrays.asList("recruiter", "admin"))
        //     .addRule("DELETE", Arrays.asList("admin"))
        // );
    }

    /**
     * Exemple 2: MS-ROADMAP Routes
     * 
     * Scénario:
     * - Recruiter crée et gère les parcours de formation
     * - Candidat peut voir les parcours disponibles
     * - Admin gère les permissions
     */
    public static void exampleForMSRoadmap() {
        // À ajouter dans initializeRouteRules():
        
        // routeRules.put("/api/v1/roadmaps", new RouteRule()
        //     .addRule("GET", Arrays.asList("recruiter", "candidate", "admin")) // Tous peuvent voir les roadmaps
        //     .addRule("POST", Arrays.asList("recruiter", "admin"))              // Recruiter crée
        //     .addRule("PUT", Arrays.asList("recruiter", "admin"))               // Recruiter modifie
        //     .addRule("DELETE", Arrays.asList("admin"))                         // Seul admin supprime
        // );
        //
        // routeRules.put("/api/v1/roadmaps/my-progress", new RouteRule()
        //     .addRule("GET", Arrays.asList("candidate"))                        // Candidat voit sa progression
        // );
        //
        // routeRules.put("/api/v1/milestones", new RouteRule()
        //     .addRule("GET", Arrays.asList("recruiter", "candidate", "admin"))  // Tous peuvent consulter
        //     .addRule("POST", Arrays.asList("recruiter", "admin"))
        //     .addRule("PUT", Arrays.asList("recruiter", "admin"))
        //     .addRule("DELETE", Arrays.asList("admin"))
        // );
    }

    /**
     * Exemple 3: Routes avec accès mixte
     * 
     * Scénario:
     * - GET est ouvert à tous
     * - POST/PUT/DELETE nécessitent recruiter ou admin
     */
    public static void exampleMixedAccess() {
        // À ajouter dans initializeRouteRules():
        
        // routeRules.put("/api/v1/skills", new RouteRule()
        //     // GET n'est pas configurée = PUBLIQUE (pas besoin d'être authen)
        //     // Ou bien:
        //     // .addRule("GET", Arrays.asList("recruiter", "candidate", "admin"))  // Tous authentifiés
        //     .addRule("POST", Arrays.asList("recruiter", "admin"))
        //     .addRule("PUT", Arrays.asList("recruiter", "admin"))
        //     .addRule("DELETE", Arrays.asList("admin"))
        // );
    }

    /**
     * ÉTAPES POUR AJOUTER VOS ROUTES:
     * 
     * 1. Identifier vos endpoints:
     *    - GET /api/v1/myresource
     *    - POST /api/v1/myresource
     *    - PUT /api/v1/myresource/{id}
     *    - DELETE /api/v1/myresource/{id}
     * 
     * 2. Ouvrir RouteAuthorizationConfig.java
     * 
     * 3. Dans initializeRouteRules(), ajouter:
     *    routeRules.put("/api/v1/myresource", new RouteRule()
     *        .addRule("GET", Arrays.asList("recruiter", "admin"))
     *        .addRule("POST", Arrays.asList("recruiter", "admin"))
     *        .addRule("PUT", Arrays.asList("recruiter", "admin"))
     *        .addRule("DELETE", Arrays.asList("admin"))
     *    );
     * 
     * 4. Les routes non configurées resteront PUBLIQUES (accessibles sans rôle)
     * 
     * 5. Les rôles sont case-insensitive (RECRUITER = recruiter = Recruiter)
     * 
     * 6. Tester avec curl:
     *    curl -X GET http://localhost:8887/api/v1/myresource \
     *      -H "Authorization: Bearer TOKEN"
     */
}

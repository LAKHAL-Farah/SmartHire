package tn.esprit.eventmanagement.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // ✅ Avec SockJS (pour les anciens navigateurs)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();





        // ✅ Sans SockJS (pour Angular avec WebSocket native)
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*");
    }
}
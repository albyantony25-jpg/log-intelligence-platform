package com.logplatform.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        // Use an in-memory message broker to route messages back to the client on destinations prefixed with "/topic"
        config.enableSimpleBroker("/topic");
        // Prefix for messages originating from the client (if any)
        config.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // Register the "/ws-logs" endpoint, enabling SockJS fallback options and allowing all origins
        registry.addEndpoint("/ws-logs").setAllowedOriginPatterns("*").withSockJS();
    }
}

package com.chat.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
@Slf4j
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final WebSocketAuthInterceptor webSocketAuthInterceptor;
    private final SecurityContextFilter securityContextFilter;
    
    // Track active sessions for better management
    private final Map<String, Map<String, Object>> activeSessions = new ConcurrentHashMap<>();

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS()
                .setClientLibraryUrl("https://cdn.jsdelivr.net/npm/sockjs-client@1/dist/sockjs.min.js");
        
        // Additional endpoint for native WebSocket
        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        // Enable simple broker with heartbeats
        ThreadPoolTaskScheduler taskScheduler = new ThreadPoolTaskScheduler();
        taskScheduler.setPoolSize(1);
        taskScheduler.setThreadNamePrefix("websocket-heartbeat-");
        taskScheduler.initialize();
        
        registry.enableSimpleBroker("/topic", "/queue", "/user")
                .setHeartbeatValue(new long[] {10000, 10000})
                .setTaskScheduler(taskScheduler);
        
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(
            webSocketAuthInterceptor,
            securityContextFilter,
            new EnhancedChannelInterceptor(activeSessions)
        );
    }
    
    // Enhanced channel interceptor for better session management
    @Order(Ordered.HIGHEST_PRECEDENCE + 100)
    private static class EnhancedChannelInterceptor implements ChannelInterceptor {
        
        private final Map<String, Map<String, Object>> activeSessions;
        
        public EnhancedChannelInterceptor(Map<String, Map<String, Object>> activeSessions) {
            this.activeSessions = activeSessions;
        }
        
        @Override
        public Message<?> preSend(Message<?> message, MessageChannel channel) {
            StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
            
            if (accessor != null) {
                String sessionId = accessor.getSessionId();
                StompCommand command = accessor.getCommand();
                
                switch (command) {
                    case CONNECT:
                        log.info("📡 New WebSocket connection: {}", sessionId);
                        break;
                    case SUBSCRIBE:
                        handleSubscription(accessor, sessionId);
                        break;
                    case DISCONNECT:
                        handleDisconnect(sessionId);
                        break;
                    case SEND:
                        log.debug("📤 Message sent via session: {}", sessionId);
                        break;
                }
            }
            
            return message;
        }
        
        private void handleSubscription(StompHeaderAccessor accessor, String sessionId) {
            String destination = accessor.getDestination();
            Map<String, Object> sessionInfo = activeSessions.get(sessionId);
            
            if (sessionInfo != null && destination != null) {
                log.debug("📡 Session {} subscribed to: {}", sessionId, destination);
                
                // Track subscription
                @SuppressWarnings("unchecked")
                Map<String, String> subscriptions = (Map<String, String>) 
                    sessionInfo.computeIfAbsent("subscriptions", k -> new ConcurrentHashMap<>());
                subscriptions.put(destination, String.valueOf(System.currentTimeMillis()));
            }
        }
        
        private void handleDisconnect(String sessionId) {
            Map<String, Object> sessionInfo = activeSessions.remove(sessionId);
            if (sessionInfo != null) {
                log.info("🔌 Session disconnected: {}", sessionId);
            }
        }
    }
}
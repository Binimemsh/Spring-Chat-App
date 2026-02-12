package com.chat.config;

import com.chat.model.User;
import com.chat.repository.UserRepository;
import com.chat.service.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {
    
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    
    // Track active sessions
    public static final Map<String, User> activeSessions = new ConcurrentHashMap<>();
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor == null) {
            return message;
        }
        
        StompCommand command = accessor.getCommand();
        String sessionId = accessor.getSessionId();
        
        // Handle null command - just return the message
        if (command == null) {
            log.trace("Received message with null command, session: {}", sessionId);
            return message;
        }
        
        log.debug("WebSocket command: {} | Session: {}", command, sessionId);
        
        try {
            switch (command) {
                case CONNECT:
                    return handleConnect(message, accessor, sessionId);
                case SUBSCRIBE:
                case SEND:
                    restoreAuthentication(accessor, sessionId);
                    break;
                case DISCONNECT:
                    handleDisconnect(sessionId);
                    break;
                default:
                    // For other commands, just restore authentication if needed
                    restoreAuthentication(accessor, sessionId);
                    break;
            }
        } catch (Exception e) {
            log.error("Error processing command {}: {}", command, e.getMessage());
        }
        
        return message;
    }
    
    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor, String sessionId) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        
        log.info("🔌 WebSocket CONNECT attempt - Session: {}", sessionId);
        
        // Check for Authorization header
        if (authHeaders == null || authHeaders.isEmpty()) {
            log.warn("⚠️ No Authorization header in CONNECT! Session: {}", sessionId);
            return null; // Reject connection
        }
        
        String authHeader = authHeaders.get(0);
        
        if (!StringUtils.hasText(authHeader) || !authHeader.startsWith("Bearer ")) {
            log.warn("⚠️ Invalid Authorization header format: {}", authHeader);
            return null; // Reject connection
        }
        
        String token = authHeader.substring(7);
        
        try {
            // Extract username and userId from token
            String username = jwtService.extractUsername(token);
            Long userId = jwtService.extractUserId(token);
            
            if (username == null || userId == null) {
                log.warn("❌ Could not extract username or userId from token");
                return null;
            }
            
            // Load user details
            UserDetails userDetails = userDetailsService.loadUserByUsername(username);
            
            // Validate token
            if (!jwtService.validateToken(token, userDetails)) {
                log.warn("❌ Token validation failed for: {}", username);
                return null;
            }
            
            // Get full user from database
            User user = userRepository.findById(userId)
                .orElseThrow(() -> new RuntimeException("User not found with ID: " + userId));
            
            // Create authentication token
            UsernamePasswordAuthenticationToken authentication = 
                new UsernamePasswordAuthenticationToken(user, null, userDetails.getAuthorities());
            
            // Store in session attributes
            Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
            if (sessionAttrs != null) {
                sessionAttrs.put("SPRING_SECURITY_CONTEXT", new SecurityContextImpl(authentication));
                sessionAttrs.put("userId", userId);
                sessionAttrs.put("username", username);
                sessionAttrs.put("user", user);
                sessionAttrs.put("AUTHENTICATION", authentication);
            }
            
            // Track active session
            activeSessions.put(sessionId, user);
            
            // Set in accessor for WebSocket
            accessor.setUser(authentication);
            
            // Set in SecurityContextHolder
            SecurityContextHolder.getContext().setAuthentication(authentication);
            
            // Update user status in database
            user.setOnline(true);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
            
            log.info("✅✅✅ WebSocket AUTHENTICATED: {} (ID: {}) | Session: {}", 
                username, userId, sessionId);
            
            return message;
            
        } catch (Exception e) {
            log.error("❌ Authentication error: {}", e.getMessage());
            return null; // Reject connection
        }
    }
    
    private void restoreAuthentication(StompHeaderAccessor accessor, String sessionId) {
        try {
            Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
            if (sessionAttrs != null) {
                Authentication auth = (Authentication) sessionAttrs.get("AUTHENTICATION");
                if (auth != null && auth.isAuthenticated()) {
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    accessor.setUser(auth);
                    log.trace("Restored authentication for: {}", auth.getName());
                }
            }
        } catch (Exception e) {
            log.debug("Could not restore authentication: {}", e.getMessage());
        }
    }
    
    private void handleDisconnect(String sessionId) {
        try {
            User user = activeSessions.remove(sessionId);
            if (user != null) {
                log.info("User disconnected: {} (session: {})", user.getUsername(), sessionId);
                
                // Update user status in database
                user.setOnline(false);
                user.setLastSeen(LocalDateTime.now());
                userRepository.save(user);
            }
        } catch (Exception e) {
            log.error("Error handling disconnect: {}", e.getMessage());
        }
    }
    
    public static List<User> getActiveUsers() {
        return activeSessions.values().stream()
                .distinct()
                .toList();
    }
    
    public static boolean isUserOnline(Long userId) {
        return activeSessions.values().stream()
                .anyMatch(user -> user.getId().equals(userId));
    }
}
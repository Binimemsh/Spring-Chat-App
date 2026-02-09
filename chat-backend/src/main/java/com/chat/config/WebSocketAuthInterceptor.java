package com.chat.config;

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
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
@Slf4j
public class WebSocketAuthInterceptor implements ChannelInterceptor {
	//https://github.com/Binimemsh/Spring-Chat-App.git
    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final SecurityContextHolderStrategy securityContextHolderStrategy = 
        SecurityContextHolder.getContextHolderStrategy();
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.CONNECT.equals(accessor.getCommand())) {
            return handleConnect(message, accessor);
        }
        
        return message;
    }
    
    private Message<?> handleConnect(Message<?> message, StompHeaderAccessor accessor) {
        List<String> authHeaders = accessor.getNativeHeader("Authorization");
        String sessionId = accessor.getSessionId();
        
        log.info("🔌 WebSocket CONNECT attempt - Session: {}", sessionId);
        
        if (authHeaders != null && !authHeaders.isEmpty()) {
            String authHeader = authHeaders.get(0);
            
            if (StringUtils.hasText(authHeader) && authHeader.startsWith("Bearer ")) {
                String token = authHeader.substring(7);
                
                try {
                    // Extract user information from token
                    String username = jwtService.extractUsername(token);
                    Long userId = jwtService.extractUserId(token);
                    
                    log.debug("Token details - Username: {}, UserId: {}", username, userId);
                    
                    if (username != null) {
                        UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                        
                        if (jwtService.validateToken(token, userDetails)) {
                            // Create authentication token with authorities - FIXED
                            UsernamePasswordAuthenticationToken authentication = 
                                new UsernamePasswordAuthenticationToken(
                                    userDetails, // principal
                                    null, // credentials - should be null when authenticated
                                    userDetails.getAuthorities() // authorities
                                );
                            // Don't call setAuthenticated(true) - it's already authenticated
                            
                            // 1. Set in accessor (for WebSocket)
                            accessor.setUser(authentication);
                            
                            // 2. Create and set SecurityContext
                            SecurityContext context = new SecurityContextImpl();
                            context.setAuthentication(authentication);
                            
                            // Store in session attributes for later use
                            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
                            if (sessionAttributes != null) {
                                sessionAttributes.put("userId", userId);
                                sessionAttributes.put("username", username);
                                sessionAttributes.put("SPRING_SECURITY_CONTEXT", context);
                            }
                            
                            // 3. Set in ThreadLocal for current thread
                            securityContextHolderStrategy.setContext(context);
                            
                            log.info("✅✅✅ WebSocket AUTHENTICATED: {} (ID: {}) | Session: {}", 
                                username, userId, sessionId);
                            
                            return message;
                        } else {
                            log.warn("❌ Token validation failed for: {}", username);
                        }
                    } else {
                        log.warn("❌ Could not extract user details from token");
                    }
                } catch (Exception e) {
                    log.error("❌ Authentication error: {}", e.getMessage());
                    log.debug("Stack trace:", e);
                }
            } else {
                log.warn("⚠️ No Bearer token found or malformed Authorization header");
            }
        } else {
            log.warn("⚠️ NO Authorization header in CONNECT!");
        }
        
        // If we get here, authentication failed
        log.warn("⚠️ WebSocket connection REJECTED - No valid authentication for session: {}", sessionId);
        return null; // Reject the connection
    }
    
    @Override
    public void postSend(Message<?> message, MessageChannel channel, boolean sent) {
        // Ensure SecurityContext is set for SEND messages
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null && StompCommand.SEND.equals(accessor.getCommand())) {
            Authentication auth = (Authentication) accessor.getUser();
            if (auth != null) {
                SecurityContext context = new SecurityContextImpl();
                context.setAuthentication(auth);
                securityContextHolderStrategy.setContext(context);
                log.debug("🔗 SecurityContext set for SEND message from: {}", auth.getName());
            }
        }
    }
}
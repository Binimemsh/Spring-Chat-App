package com.chat.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 99)
@Slf4j
public class SecurityContextFilter implements ChannelInterceptor {
    
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        
        if (accessor != null) {
            // Try to get authentication from session attributes
            Map<String, Object> sessionAttrs = accessor.getSessionAttributes();
            if (sessionAttrs != null) {
                SecurityContext context = (SecurityContext) sessionAttrs.get("SPRING_SECURITY_CONTEXT");
                if (context != null && context.getAuthentication() != null) {
                    // Set in SecurityContextHolder
                    SecurityContextHolder.setContext(context);
                    log.debug("🔗 Restored SecurityContext from session for user: {}", 
                        context.getAuthentication().getName());
                }
            }
            
            // Also try to get from accessor user
            Authentication auth = (Authentication) accessor.getUser();
            if (auth != null && auth.isAuthenticated()) {
                SecurityContextHolder.getContext().setAuthentication(auth);
                log.debug("🔗 Set SecurityContext from accessor for user: {}", auth.getName());
            }
        }
        
        return message;
    }
}
package com.chat.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.security.authorization.AuthorizationManager;
import org.springframework.security.config.annotation.web.socket.EnableWebSocketSecurity;
import org.springframework.security.messaging.access.intercept.MessageMatcherDelegatingAuthorizationManager;

import static org.springframework.messaging.simp.SimpMessageType.*;

//@Configuration
//@EnableWebSocketSecurity
public class WebSocketSecurityConfig {
    
  //  @Bean
    public AuthorizationManager<Message<?>> messageAuthorizationManager(
            MessageMatcherDelegatingAuthorizationManager.Builder messages) {
        
        return messages
            // Allow connection and heartbeat
            .simpTypeMatchers(CONNECT, CONNECT_ACK, HEARTBEAT, 
                            DISCONNECT, DISCONNECT_ACK, OTHER).permitAll()
            
            // Allow users to join without authentication initially
            .simpDestMatchers("/app/chat.addUser").permitAll()
            
            // Secure other message endpoints
            .simpDestMatchers("/app/chat.sendMessage").authenticated()
            .simpDestMatchers("/app/chat.private").authenticated()
            .simpDestMatchers("/app/chat.typing").authenticated()
            .simpDestMatchers("/app/chat.getActiveUsers").authenticated()
            
            // Secure subscription endpoints
            .simpSubscribeDestMatchers("/topic/public").permitAll()  // Allow viewing public chat
            .simpSubscribeDestMatchers("/topic/activeUsers").permitAll()
            .simpSubscribeDestMatchers("/user/queue/private").authenticated()
            
            // Deny all other messages
            .anyMessage().denyAll()
            .build();
    }
}
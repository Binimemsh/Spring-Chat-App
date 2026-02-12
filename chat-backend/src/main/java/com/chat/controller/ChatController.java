package com.chat.controller;

import com.chat.dto.ChatMessageDTO;
import com.chat.model.ChatMessage;
import com.chat.model.User;
import com.chat.repository.UserRepository;
import com.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Controller
@RequiredArgsConstructor
@Slf4j
public class ChatController {
    
    private final SimpMessagingTemplate messagingTemplate;
    private final ChatService chatService;
    private final UserRepository userRepository;
    
    private final Map<String, String> userSessions = new ConcurrentHashMap<>();
    
    @EventListener
    public void handleWebSocketConnectListener(SessionConnectedEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        log.info("New WebSocket connection: {}", sessionId);
    }
    
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String username = userSessions.get(sessionId);
        
        if (username != null) {
            log.info("User disconnected: {} (session: {})", username, sessionId);
            userSessions.remove(sessionId);
            
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                user.setOnline(false);
                user.setLastSeen(LocalDateTime.now());
                userRepository.save(user);
            }
            
            ChatMessageDTO chatMessage = new ChatMessageDTO();
            chatMessage.setType(ChatMessage.MessageType.LEAVE);
            chatMessage.setSender(username);
            chatMessage.setContent(username + " left the chat!");
            chatMessage.setTimestamp(LocalDateTime.now());
            
            messagingTemplate.convertAndSend("/topic/public", chatMessage);
        }
    }
    
    @MessageMapping("/chat.sendMessage")
    @SendTo("/topic/public")
    public ChatMessageDTO sendMessage(@Payload ChatMessageDTO chatMessageDTO,
                                     SimpMessageHeaderAccessor headerAccessor) {
        
        log.info("[Controller] Received public message");
        
        // Get user from authentication
        Authentication auth = (Authentication) headerAccessor.getUser();
        User user = null;
        
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();
            if (principal instanceof User) {
                user = (User) principal;
            } else {
                Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
                if (sessionAttrs != null) {
                    user = (User) sessionAttrs.get("user");
                }
            }
        }
        
        if (user == null) {
            log.error("No authenticated user found!");
            return null;
        }
        
        log.info("Processing message from '{}' (ID: {}): {}", 
            user.getUsername(), user.getId(), chatMessageDTO.getContent());
        
        chatMessageDTO.setSender(user.getUsername());
        chatMessageDTO.setSenderId(user.getId());
        chatMessageDTO.setTimestamp(LocalDateTime.now());
        chatMessageDTO.setType(ChatMessage.MessageType.CHAT);
        chatMessageDTO.setRoomId("general");
        
        try {
            ChatMessage savedMessage = chatService.saveMessage(chatMessageDTO, user);
            chatMessageDTO.setId(savedMessage.getId());
            log.info("Saved public message ID: {}", savedMessage.getId());
        } catch (Exception e) {
            log.error("❌ Save error: {}", e.getMessage());
        }
        
        return chatMessageDTO;
    }
    
    @MessageMapping("/chat.addUser")
    @SendTo("/topic/public")
    public ChatMessageDTO addUser(@Payload ChatMessageDTO chatMessageDTO,
                                 SimpMessageHeaderAccessor headerAccessor) {
        Authentication auth = (Authentication) headerAccessor.getUser();
        
        if (auth == null) {
            log.error("No authentication for addUser");
            return null;
        }
        
        Object principal = auth.getPrincipal();
        User user = null;
        
        if (principal instanceof User) {
            user = (User) principal;
        } else {
            Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
            if (sessionAttrs != null) {
                user = (User) sessionAttrs.get("user");
            }
        }
        
        if (user == null) {
            log.error("Could not get user from authentication");
            return null;
        }
        
        String sessionId = headerAccessor.getSessionId();
        log.info("User joined: {} (session: {})", user.getUsername(), sessionId);
        
        userSessions.put(sessionId, user.getUsername());
        
        user.setOnline(true);
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);
        
        chatMessageDTO.setSender(user.getUsername());
        chatMessageDTO.setType(ChatMessage.MessageType.JOIN);
        chatMessageDTO.setTimestamp(LocalDateTime.now());
        chatMessageDTO.setContent(user.getUsername() + " joined the chat!");
        
        return chatMessageDTO;
    }
    
    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Payload ChatMessageDTO chatMessageDTO,
                                  SimpMessageHeaderAccessor headerAccessor) {
        
        log.info("[PRIVATE] Received private message request");
        
        Authentication auth = (Authentication) headerAccessor.getUser();
        User sender = null;
        
        if (auth != null && auth.isAuthenticated()) {
            Object principal = auth.getPrincipal();
            if (principal instanceof User) {
                sender = (User) principal;
            } else {
                Map<String, Object> sessionAttrs = headerAccessor.getSessionAttributes();
                if (sessionAttrs != null) {
                    sender = (User) sessionAttrs.get("user");
                }
            }
        }
        
        if (sender == null) {
            log.error("No sender found!");
            return;
        }
        
        Long receiverId = chatMessageDTO.getReceiverId();
        if (receiverId == null) {
            log.error("No receiver specified!");
            return;
        }
        
        log.info("🔒 Private message from '{}' (ID:{}) to user ID: {}", 
            sender.getUsername(), sender.getId(), receiverId);
        
        chatMessageDTO.setSender(sender.getUsername());
        chatMessageDTO.setSenderId(sender.getId());
        chatMessageDTO.setTimestamp(LocalDateTime.now());
        chatMessageDTO.setType(ChatMessage.MessageType.CHAT);
        
        ChatMessage savedMessage = chatService.saveMessage(chatMessageDTO, sender);
        ChatMessageDTO savedDTO = ChatMessageDTO.fromEntity(savedMessage);
        
        log.info("Saved private message ID: {}", savedMessage.getId());
        
        // Send to receiver
        messagingTemplate.convertAndSendToUser(
            receiverId.toString(),
            "/queue/private",
            savedDTO
        );
        log.info("Sent to user ID {} at /user/{}/queue/private", receiverId, receiverId);
        
        // Send to sender (for their own view)
        messagingTemplate.convertAndSendToUser(
            sender.getId().toString(),
            "/queue/private",
            savedDTO
        );
        log.info("Sent copy to sender ID: {}", sender.getId());
    }
    
    @MessageMapping("/chat.typing")
    @SendTo("/topic/public")
    public ChatMessageDTO typing(@Payload ChatMessageDTO chatMessageDTO,
                                SimpMessageHeaderAccessor headerAccessor) {
        Authentication auth = (Authentication) headerAccessor.getUser();
        
        if (auth == null) {
            return null;
        }
        
        Object principal = auth.getPrincipal();
        String username = null;
        
        if (principal instanceof User) {
            username = ((User) principal).getUsername();
        } else if (principal instanceof String) {
            username = (String) principal;
        }
        
        if (username == null) {
            return null;
        }
        
        chatMessageDTO.setSender(username);
        chatMessageDTO.setType(ChatMessage.MessageType.TYPING);
        chatMessageDTO.setTimestamp(LocalDateTime.now());
        chatMessageDTO.setContent(username + " is typing...");
        
        return chatMessageDTO;
    }
    
    @MessageMapping("/chat.ping")
    @SendTo("/topic/ping")
    public String ping() {
        log.info("Ping received");
        return "pong - " + LocalDateTime.now();
    }
}
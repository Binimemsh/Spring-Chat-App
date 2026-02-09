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
        log.info("✅ New WebSocket connection established: {}", sessionId);
    }
    
    @EventListener
    public void handleWebSocketDisconnectListener(SessionDisconnectEvent event) {
        StompHeaderAccessor headerAccessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = headerAccessor.getSessionId();
        String username = userSessions.get(sessionId);
        
        if (username != null) {
            log.info("User disconnected: {} (session: {})", username, sessionId);
            userSessions.remove(sessionId);
            
            // Update user status
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                user.setOnline(false);
                user.setLastSeen(LocalDateTime.now());
                userRepository.save(user);
            }
            
            // Notify other users
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
        
        log.info("💬 [Controller] Received sendMessage request");
        
        // Get user from authentication
        Authentication auth = (Authentication) headerAccessor.getUser();
        String username = null;
        Long userId = null;
        
        if (auth != null && auth.isAuthenticated()) {
            if (auth.getPrincipal() instanceof User) {
                User user = (User) auth.getPrincipal();
                username = user.getUsername();
                userId = user.getId();
            } else if (auth.getPrincipal() instanceof org.springframework.security.core.userdetails.User) {
                org.springframework.security.core.userdetails.User userDetails = 
                    (org.springframework.security.core.userdetails.User) auth.getPrincipal();
                username = userDetails.getUsername();
                // Get user from database
                User user = userRepository.findByUsername(username).orElse(null);
                if (user != null) {
                    userId = user.getId();
                }
            } else if (auth.getPrincipal() instanceof String) {
                username = (String) auth.getPrincipal();
                User user = userRepository.findByUsername(username).orElse(null);
                if (user != null) {
                    userId = user.getId();
                }
            }
        }
        
        // Fallback to sender in DTO
        if (username == null && chatMessageDTO.getSender() != null) {
            username = chatMessageDTO.getSender();
            User user = userRepository.findByUsername(username).orElse(null);
            if (user != null) {
                userId = user.getId();
            }
        }
        
        if (username == null) {
            username = "unknown_user";
            log.warn("⚠️ Could not determine username, using fallback: {}", username);
        }
        
        log.info("💬 Processing message from '{}' (ID: {}): {}", username, userId, chatMessageDTO.getContent());
        
        // Set sender and timestamp
        chatMessageDTO.setSender(username);
        chatMessageDTO.setSenderId(userId);
        chatMessageDTO.setTimestamp(LocalDateTime.now());
        chatMessageDTO.setType(ChatMessage.MessageType.CHAT);
        
        // Save to database
        try {
            ChatMessage savedMessage = chatService.saveMessage(chatMessageDTO);
            chatMessageDTO.setId(savedMessage.getId());
            log.info("💾 Saved message ID: {}", savedMessage.getId());
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
            log.error("No authentication found for addUser");
            return null;
        }
        
        String username = auth.getName();
        String sessionId = headerAccessor.getSessionId();
        
        log.info("User joined: {} (session: {})", username, sessionId);
        
        // Store user session
        userSessions.put(sessionId, username);
        
        // Update user status
        User user = userRepository.findByUsername(username).orElse(null);
        if (user != null) {
            user.setOnline(true);
            user.setLastSeen(LocalDateTime.now());
            userRepository.save(user);
        }
        
        chatMessageDTO.setSender(username);
        chatMessageDTO.setType(ChatMessage.MessageType.JOIN);
        chatMessageDTO.setTimestamp(LocalDateTime.now());
        chatMessageDTO.setContent(username + " joined the chat!");
        
        return chatMessageDTO;
    }
    
    @MessageMapping("/chat.typing")
    @SendTo("/topic/public")
    public ChatMessageDTO typing(@Payload ChatMessageDTO chatMessageDTO,
                                SimpMessageHeaderAccessor headerAccessor) {
        Authentication auth = (Authentication) headerAccessor.getUser();
        
        if (auth == null) {
            return null;
        }
        
        String username = auth.getName();
        
        chatMessageDTO.setSender(username);
        chatMessageDTO.setType(ChatMessage.MessageType.TYPING);
        chatMessageDTO.setTimestamp(LocalDateTime.now());
        chatMessageDTO.setContent(username + " is typing...");
        
        return chatMessageDTO;
    }
    
    @MessageMapping("/chat.private")
    public void sendPrivateMessage(@Payload ChatMessageDTO chatMessageDTO,
                                  SimpMessageHeaderAccessor headerAccessor) {
        
        log.info("🔒 [PRIVATE] Received private message request");
        
        // Get sender info
        String sender = null;
        Long senderId = null;
        
        if (headerAccessor != null) {
            Authentication auth = (Authentication) headerAccessor.getUser();
            if (auth != null && auth.isAuthenticated()) {
                Object principal = auth.getPrincipal();
                if (principal instanceof User) {
                    sender = ((User) principal).getUsername();
                    senderId = ((User) principal).getId();
                } else if (principal instanceof org.springframework.security.core.userdetails.User) {
                    sender = ((org.springframework.security.core.userdetails.User) principal).getUsername();
                    User senderUser = userRepository.findByUsername(sender).orElse(null);
                    if (senderUser != null) {
                        senderId = senderUser.getId();
                    }
                }
            }
        }
        
        // Fallback to DTO sender
        if (sender == null && chatMessageDTO.getSender() != null) {
            sender = chatMessageDTO.getSender();
            User senderUser = userRepository.findByUsername(sender).orElse(null);
            if (senderUser != null) {
                senderId = senderUser.getId();
            }
        }
        
        Long receiverId = chatMessageDTO.getReceiverId();
        
        if (sender == null || senderId == null || receiverId == null) {
            log.error("❌ Cannot send private: sender={}, senderId={}, receiverId={}", 
                sender, senderId, receiverId);
            return;
        }
        
        log.info("🔒 Private message from '{}' (ID:{}) to user ID: {}", sender, senderId, receiverId);
        
        // Set sender info in DTO
        chatMessageDTO.setSender(sender);
        chatMessageDTO.setSenderId(senderId);
        chatMessageDTO.setTimestamp(LocalDateTime.now());
        chatMessageDTO.setType(ChatMessage.MessageType.CHAT);
        
        // Save to database
        ChatMessage savedMessage = chatService.saveMessage(chatMessageDTO);
        ChatMessageDTO savedDTO = ChatMessageDTO.fromEntity(savedMessage);
        
        log.info("💾 Saved private message ID: {}", savedMessage.getId());
        
        // Send to receiver (user-specific destination)
        messagingTemplate.convertAndSendToUser(
            receiverId.toString(),
            "/queue/private",
            savedDTO
        );
        log.info("📤 Sent to user ID {} at /user/{}/queue/private", receiverId, receiverId);
        
        // Also send to sender (so they see their own message)
        messagingTemplate.convertAndSendToUser(
            senderId.toString(),
            "/queue/private",
            savedDTO
        );
        log.info("📥 Sent copy to sender ID: {}", senderId);
    }
    
    // Simple ping endpoint for testing
    @MessageMapping("/chat.ping")
    @SendTo("/topic/ping")
    public String ping() {
        log.info("🏓 Ping received");
        return "pong - " + LocalDateTime.now();
    }
}
package com.chat.controller;

import com.chat.dto.ApiResponse;
import com.chat.dto.ChatMessageDTO;
import com.chat.dto.UserDTO;
import com.chat.model.User;
import com.chat.repository.UserRepository;
import com.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
@Slf4j
public class RestChatController {
    
    private final ChatService chatService;
    private final UserRepository userRepository;
    
    @GetMapping("/messages/{roomId}")
    public ResponseEntity<ApiResponse> getRoomMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        log.info("Fetching messages for room: {}, limit: {}, offset: {}", roomId, limit, offset);
        List<ChatMessageDTO> messages = chatService.getMessagesByRoom(roomId, limit, offset);
        return ResponseEntity.ok(ApiResponse.success("Messages retrieved", messages));
    }
    
    @GetMapping("/messages/private/{userId}")
    public ResponseEntity<ApiResponse> getPrivateMessages(
            @PathVariable Long userId,
            @RequestParam Long otherUserId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        log.info("Fetching private messages between {} and {}, limit: {}, offset: {}", 
            userId, otherUserId, limit, offset);
        List<ChatMessageDTO> messages = chatService.getPrivateMessages(userId, otherUserId, limit, offset);
        return ResponseEntity.ok(ApiResponse.success("Private messages retrieved", messages));
    }
    
    @GetMapping("/users/online")
    public ResponseEntity<ApiResponse> getOnlineUsers() {
        log.info("Fetching online users");
        List<UserDTO> onlineUsers = chatService.getOnlineUsers();
        return ResponseEntity.ok(ApiResponse.success("Online users retrieved", onlineUsers));
    }
    
    @GetMapping("/users/search")
    public ResponseEntity<ApiResponse> searchUsers(@RequestParam String query) {
        log.info("Searching users with query: {}", query);
        // FIX: chatService.searchUsers returns List<UserDTO>
        List<UserDTO> users = chatService.searchUsers(query);
        return ResponseEntity.ok(ApiResponse.success("Users found", users));
    }
    
    @GetMapping("/rooms")
    public ResponseEntity<ApiResponse> getChatRooms() {
        return ResponseEntity.ok(ApiResponse.success("Chat rooms retrieved", 
            chatService.getPublicRooms()));
    }
    
    @GetMapping("/rooms/public")
    public ResponseEntity<ApiResponse> getPublicRooms() {
        return ResponseEntity.ok(ApiResponse.success("Public chat rooms retrieved", 
            chatService.getPublicRooms()));
    }
    
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse> getRecentConversations(Authentication authentication) {
        User user = userRepository.findByUsername(authentication.getName())
                .orElseThrow(() -> new RuntimeException("User not found"));
        
        List<Map<String, Object>> conversations = chatService.getRecentConversations(user.getId());
        return ResponseEntity.ok(ApiResponse.success("Conversations retrieved", conversations));
    }
    
    @PostMapping("/messages/{messageId}/read")
    public ResponseEntity<ApiResponse> markMessageAsRead(@PathVariable Long messageId) {
        chatService.markMessageAsRead(messageId);
        return ResponseEntity.ok(ApiResponse.success("Message marked as read", null));
    }
    
    @PostMapping("/messages/read-all")
    public ResponseEntity<ApiResponse> markAllMessagesAsRead(
            @RequestParam Long userId,
            @RequestParam Long otherUserId) {
        
        chatService.markAllMessagesAsRead(userId, otherUserId);
        return ResponseEntity.ok(ApiResponse.success("All messages marked as read", null));
    }
    
    @GetMapping("/messages/unread/count")
    public ResponseEntity<ApiResponse> getUnreadMessageCount(
            @RequestParam Long userId,
            @RequestParam Long otherUserId) {
        
        long count = chatService.getUnreadMessageCount(userId, otherUserId);
        return ResponseEntity.ok(ApiResponse.success("Unread message count", count));
    }
}
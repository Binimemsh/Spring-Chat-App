package com.chat.controller;

import com.chat.dto.ApiResponse;
import com.chat.dto.ChatMessageDTO;
import com.chat.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class RestChatController {
    
    private final ChatService chatService;
    
    @GetMapping("/messages/{roomId}")
    public ResponseEntity<ApiResponse> getRoomMessages(
            @PathVariable String roomId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        List<ChatMessageDTO> messages = chatService.getMessagesByRoom(roomId, limit, offset);
        return ResponseEntity.ok(ApiResponse.success("Messages retrieved", messages));
    }
    
    @GetMapping("/messages/private/{userId}")
    public ResponseEntity<ApiResponse> getPrivateMessages(
            @PathVariable Long userId,
            @RequestParam Long otherUserId,
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(defaultValue = "0") int offset) {
        
        List<ChatMessageDTO> messages = chatService.getPrivateMessages(userId, otherUserId, limit, offset);
        return ResponseEntity.ok(ApiResponse.success("Private messages retrieved", messages));
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
    
    @GetMapping("/users/online")
    public ResponseEntity<ApiResponse> getOnlineUsers() {
        return ResponseEntity.ok(ApiResponse.success("Online users retrieved",
            chatService.getOnlineUsers()));
    }
    
    @GetMapping("/users/search")
    public ResponseEntity<ApiResponse> searchUsers(@RequestParam String query) {
        return ResponseEntity.ok(ApiResponse.success("Users found",
            chatService.searchUsers(query)));
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
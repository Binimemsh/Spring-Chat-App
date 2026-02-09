package com.chat.service;

import com.chat.dto.ChatMessageDTO;
import com.chat.model.ChatMessage;
import com.chat.model.ChatRoom;
import com.chat.model.User;
import com.chat.repository.ChatMessageRepository;
import com.chat.repository.ChatRoomRepository;
import com.chat.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ChatService {
    
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    
    public ChatMessage saveMessage(ChatMessageDTO chatMessageDTO) {
        User sender = userRepository.findByUsername(chatMessageDTO.getSender())
                .orElseThrow(() -> new RuntimeException("User not found: " + chatMessageDTO.getSender()));
        
        ChatMessage message = new ChatMessage();
        message.setType(chatMessageDTO.getType());
        message.setContent(chatMessageDTO.getContent());
        message.setSender(sender);
        message.setReceiverId(chatMessageDTO.getReceiverId());
        message.setRoomId(chatMessageDTO.getRoomId());
        message.setTimestamp(LocalDateTime.now());
        message.setRead(false);
        
        return chatMessageRepository.save(message);
    }
    
    public List<ChatMessageDTO> getMessagesByRoom(String roomId, int limit, int offset) {
        // Calculate page number
        int page = offset / limit;
        Pageable pageable = PageRequest.of(page, limit, Sort.by("timestamp").descending());
        
        // Get all messages and then apply pagination manually since we don't have the repository method
        List<ChatMessage> allMessages = chatMessageRepository.findByRoomId(roomId);
        
        return allMessages.stream()
                .skip(offset)
                .limit(limit)
                .map(ChatMessageDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    public List<ChatMessageDTO> getPrivateMessages(Long user1Id, Long user2Id, int limit, int offset) {
        // Get all private messages between two users
        List<ChatMessage> allMessages = chatMessageRepository.findPrivateMessages(user1Id, user2Id);
        
        return allMessages.stream()
                .skip(offset)
                .limit(limit)
                .map(ChatMessageDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    public List<ChatRoom> getPublicRooms() {
        return chatRoomRepository.findPublicRooms();
    }
    
    public ChatRoom createRoom(ChatRoom room, User creator) {
        room.setCreatedBy(creator);
        room.setCreatedAt(LocalDateTime.now());
        room.getMembers().add(creator);
        return chatRoomRepository.save(room);
    }
    
    public ChatRoom joinRoom(Long roomId, User user) {
        ChatRoom room = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new RuntimeException("Room not found"));
        
        if (!room.getMembers().contains(user)) {
            room.getMembers().add(user);
            return chatRoomRepository.save(room);
        }
        
        return room;
    }
    
    public List<User> getOnlineUsers() {
        return userRepository.findOnlineUsers();
    }
    
    public List<User> searchUsers(String query) {
        return userRepository.searchByUsername(query);
    }
    
    public void markMessageAsRead(Long messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        message.setRead(true);
        chatMessageRepository.save(message);
    }
    
    public void markAllMessagesAsRead(Long userId, Long otherUserId) {
        List<ChatMessage> messages = chatMessageRepository.findPrivateMessages(userId, otherUserId);
        messages.forEach(msg -> {
            if (msg.getSender().getId().equals(otherUserId)) {
                msg.setRead(true);
            }
        });
        chatMessageRepository.saveAll(messages);
    }
    
    public long getUnreadMessageCount(Long userId, Long otherUserId) {
        // Count unread messages from otherUserId to userId
        List<ChatMessage> messages = chatMessageRepository.findPrivateMessages(userId, otherUserId);
        return messages.stream()
                .filter(msg -> msg.getSender().getId().equals(otherUserId) && !msg.getRead())
                .count();
    }
    
    // New method: Get recent conversations
    public List<Map<String, Object>> getRecentConversations(Long userId) {
        List<Map<String, Object>> conversations = new ArrayList<>();
        
        // Get all users the current user has chatted with
        List<ChatMessage> allUserMessages = chatMessageRepository.findAll()
                .stream()
                .filter(msg -> 
                    (msg.getSender().getId().equals(userId) && msg.getReceiverId() != null) ||
                    (msg.getReceiverId() != null && msg.getReceiverId().equals(userId))
                )
                .collect(Collectors.toList());
        
        // Group by other user
        Map<Long, List<ChatMessage>> messagesByUser = allUserMessages.stream()
                .collect(Collectors.groupingBy(msg -> {
                    if (msg.getSender().getId().equals(userId)) {
                        return msg.getReceiverId();
                    } else {
                        return msg.getSender().getId();
                    }
                }));
        
        // Create conversation objects
        for (Map.Entry<Long, List<ChatMessage>> entry : messagesByUser.entrySet()) {
            Long otherUserId = entry.getKey();
            List<ChatMessage> userMessages = entry.getValue();
            
            // Sort messages by timestamp to get the latest
            userMessages.sort((m1, m2) -> m2.getTimestamp().compareTo(m1.getTimestamp()));
            
            ChatMessage lastMessage = userMessages.get(0);
            
            // Get user info
            User otherUser = userRepository.findById(otherUserId).orElse(null);
            if (otherUser != null) {
                // Count unread messages
                long unreadCount = userMessages.stream()
                        .filter(msg -> msg.getSender().getId().equals(otherUserId) && !msg.getRead())
                        .count();
                
                Map<String, Object> conversation = new HashMap<>();
                conversation.put("otherUserId", otherUserId);
                conversation.put("otherUsername", otherUser.getUsername());
                conversation.put("lastMessage", lastMessage.getContent());
                conversation.put("lastMessageTime", lastMessage.getTimestamp());
                conversation.put("unreadCount", unreadCount);
                
                conversations.add(conversation);
            }
        }
        
        // Sort by last message time
        conversations.sort((c1, c2) -> 
            ((LocalDateTime) c2.get("lastMessageTime")).compareTo((LocalDateTime) c1.get("lastMessageTime"))
        );
        
        return conversations;
    }
    
    // New method: Get messages with pagination support
    public List<ChatMessageDTO> getMessagesWithPagination(String roomId, int page, int size) {
        // This is a simplified version - in production you'd want proper pagination
        List<ChatMessage> messages = chatMessageRepository.findByRoomId(roomId);
        
        int start = page * size;
        int end = Math.min(start + size, messages.size());
        
        if (start >= messages.size()) {
            return new ArrayList<>();
        }
        
        return messages.subList(start, end)
                .stream()
                .map(ChatMessageDTO::fromEntity)
                .collect(Collectors.toList());
    }
}
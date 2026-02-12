package com.chat.service;

import com.chat.config.WebSocketAuthInterceptor;
import com.chat.dto.ChatMessageDTO;
import com.chat.dto.UserDTO;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class ChatService {
    
    private final ChatMessageRepository chatMessageRepository;
    private final UserRepository userRepository;
    private final ChatRoomRepository chatRoomRepository;
    
    public ChatMessage saveMessage(ChatMessageDTO dto, User sender) {
        ChatMessage message = new ChatMessage();
        message.setType(dto.getType());
        message.setContent(dto.getContent());
        message.setSender(sender);
        message.setReceiverId(dto.getReceiverId());
        message.setRoomId(dto.getRoomId() != null ? dto.getRoomId() : "general");
        message.setTimestamp(LocalDateTime.now());
        message.setRead(false);
        
        return chatMessageRepository.save(message);
    }
    
    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getMessagesByRoom(String roomId, int limit, int offset) {
        int page = offset / limit;
        Pageable pageable = PageRequest.of(page, limit, Sort.by("timestamp").descending());
        List<ChatMessage> messages = chatMessageRepository.findByRoomIdOrderByTimestampDesc(roomId, pageable);
        
        // Reverse to get chronological order
        Collections.reverse(messages);
        
        return messages.stream()
                .map(ChatMessageDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ChatMessageDTO> getPrivateMessages(Long user1Id, Long user2Id, int limit, int offset) {
        int page = offset / limit;
        Pageable pageable = PageRequest.of(page, limit, Sort.by("timestamp").descending());
        List<ChatMessage> messages = chatMessageRepository.findPrivateMessages(user1Id, user2Id, pageable);
        
        // Reverse to get chronological order
        Collections.reverse(messages);
        
        return messages.stream()
                .map(ChatMessageDTO::fromEntity)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<UserDTO> getOnlineUsers() {
        // Get users from active WebSocket sessions
        List<User> onlineUsers = new ArrayList<>(WebSocketAuthInterceptor.activeSessions.values()
                .stream()
                .collect(Collectors.toMap(
                    User::getId,
                    user -> user,
                    (existing, replacement) -> existing
                ))
                .values());
        
        log.info("Found {} online users from WebSocket sessions", onlineUsers.size());
        
        // Update database status
        for (User user : onlineUsers) {
            User dbUser = userRepository.findById(user.getId()).orElse(null);
            if (dbUser != null) {
                dbUser.setOnline(true);
                dbUser.setLastSeen(LocalDateTime.now());
                userRepository.save(dbUser);
            }
        }
        
        // Set offline for users not in active sessions
        List<User> allUsers = userRepository.findAll();
        for (User user : allUsers) {
            boolean isOnline = onlineUsers.stream()
                    .anyMatch(u -> u.getId().equals(user.getId()));
            if (!isOnline && user.getOnline()) {
                user.setOnline(false);
                userRepository.save(user);
            }
        }
        
        return onlineUsers.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<UserDTO> searchUsers(String query) {
        List<User> users = userRepository.searchByUsername(query);
        return users.stream()
                .map(this::convertToDTO)
                .collect(Collectors.toList());
    }
    
    @Transactional(readOnly = true)
    public List<ChatRoom> getPublicRooms() {
        List<ChatRoom> rooms = chatRoomRepository.findPublicRooms();
        if (rooms.isEmpty()) {
            // Create default rooms if none exist
            ChatRoom general = new ChatRoom();
            general.setName("general");
            general.setDescription("General Chat Room");
            general.setIsPrivate(false);
            chatRoomRepository.save(general);
            
            ChatRoom random = new ChatRoom();
            random.setName("random");
            random.setDescription("Random Discussions");
            random.setIsPrivate(false);
            chatRoomRepository.save(random);
            
            rooms = chatRoomRepository.findPublicRooms();
        }
        return rooms;
    }
    
    public void markMessageAsRead(Long messageId) {
        ChatMessage message = chatMessageRepository.findById(messageId)
                .orElseThrow(() -> new RuntimeException("Message not found"));
        message.setRead(true);
        chatMessageRepository.save(message);
    }
    
    public void markAllMessagesAsRead(Long userId, Long otherUserId) {
        List<ChatMessage> messages = chatMessageRepository.findPrivateMessages(userId, otherUserId);
        messages.stream()
                .filter(msg -> msg.getSender().getId().equals(otherUserId) && !msg.getRead())
                .forEach(msg -> msg.setRead(true));
        chatMessageRepository.saveAll(messages);
        log.info("Marked {} messages as read between {} and {}", messages.size(), userId, otherUserId);
    }
    
    @Transactional(readOnly = true)
    public long getUnreadMessageCount(Long userId, Long otherUserId) {
        return chatMessageRepository.countUnreadMessages(userId, otherUserId);
    }
    
    @Transactional(readOnly = true)
    public List<Map<String, Object>> getRecentConversations(Long userId) {
        List<Map<String, Object>> conversations = new ArrayList<>();
        
        // Get all users the current user has chatted with
        List<ChatMessage> allMessages = chatMessageRepository.findAll();
        
        Map<Long, List<ChatMessage>> messagesByUser = new HashMap<>();
        
        for (ChatMessage msg : allMessages) {
            Long otherUserId = null;
            if (msg.getSender().getId().equals(userId) && msg.getReceiverId() != null) {
                otherUserId = msg.getReceiverId();
            } else if (msg.getReceiverId() != null && msg.getReceiverId().equals(userId)) {
                otherUserId = msg.getSender().getId();
            }
            
            if (otherUserId != null) {
                messagesByUser.computeIfAbsent(otherUserId, k -> new ArrayList<>()).add(msg);
            }
        }
        
        for (Map.Entry<Long, List<ChatMessage>> entry : messagesByUser.entrySet()) {
            Long otherUserId = entry.getKey();
            List<ChatMessage> userMessages = entry.getValue();
            
            userMessages.sort((m1, m2) -> m2.getTimestamp().compareTo(m1.getTimestamp()));
            ChatMessage lastMessage = userMessages.get(0);
            
            User otherUser = userRepository.findById(otherUserId).orElse(null);
            if (otherUser != null) {
                long unreadCount = userMessages.stream()
                        .filter(msg -> msg.getSender().getId().equals(otherUserId) && !msg.getRead())
                        .count();
                
                Map<String, Object> conversation = new HashMap<>();
                conversation.put("otherUserId", otherUserId);
                conversation.put("otherUsername", otherUser.getUsername());
                conversation.put("otherUserEmail", otherUser.getEmail());
                conversation.put("otherUserOnline", otherUser.getOnline());
                conversation.put("lastMessage", lastMessage.getContent());
                conversation.put("lastMessageTime", lastMessage.getTimestamp().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
                conversation.put("unreadCount", unreadCount);
                
                conversations.add(conversation);
            }
        }
        
        // Sort by last message time (most recent first)
        conversations.sort((c1, c2) -> 
            ((String) c2.get("lastMessageTime")).compareTo((String) c1.get("lastMessageTime"))
        );
        
        return conversations;
    }
    
    private UserDTO convertToDTO(User user) {
        return new UserDTO(
                user.getId(),
                user.getUsername(),
                user.getEmail(),
                user.getFirstName(),
                user.getLastName(),
                user.getProfilePictureUrl(),
                user.getOnline(),
                user.getLastSeen(),
                user.getCreatedAt(),
                user.getRoles()
        );
    }
}
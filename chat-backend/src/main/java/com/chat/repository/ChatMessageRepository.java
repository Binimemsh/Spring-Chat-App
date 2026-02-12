package com.chat.repository;

import com.chat.model.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId ORDER BY m.timestamp DESC")
    List<ChatMessage> findByRoomIdOrderByTimestampDesc(@Param("roomId") String roomId, Pageable pageable);
    
    @Query("SELECT m FROM ChatMessage m WHERE " +
           "(m.sender.id = :user1Id AND m.receiverId = :user2Id) OR " +
           "(m.sender.id = :user2Id AND m.receiverId = :user1Id) " +
           "ORDER BY m.timestamp DESC")
    List<ChatMessage> findPrivateMessages(@Param("user1Id") Long user1Id, 
                                          @Param("user2Id") Long user2Id,
                                          Pageable pageable);
    
    // Overloaded method without Pageable for backward compatibility
    @Query("SELECT m FROM ChatMessage m WHERE " +
           "(m.sender.id = :user1Id AND m.receiverId = :user2Id) OR " +
           "(m.sender.id = :user2Id AND m.receiverId = :user1Id) " +
           "ORDER BY m.timestamp ASC")
    List<ChatMessage> findPrivateMessages(@Param("user1Id") Long user1Id, 
                                          @Param("user2Id") Long user2Id);
    
    @Query("SELECT COUNT(m) FROM ChatMessage m WHERE " +
           "m.sender.id = :otherUserId AND m.receiverId = :userId AND m.read = false")
    long countUnreadMessages(@Param("userId") Long userId, 
                            @Param("otherUserId") Long otherUserId);
    
    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND m.id > :lastMessageId ORDER BY m.timestamp ASC")
    List<ChatMessage> findNewMessages(@Param("roomId") String roomId, 
                                      @Param("lastMessageId") Long lastMessageId);
}
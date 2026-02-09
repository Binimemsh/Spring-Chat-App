package com.chat.repository;

import com.chat.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {
    
    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId ORDER BY m.timestamp ASC")
    List<ChatMessage> findByRoomId(@Param("roomId") String roomId);
    
    @Query("SELECT m FROM ChatMessage m WHERE " +
           "(m.sender.id = :user1Id AND m.receiverId = :user2Id) OR " +
           "(m.sender.id = :user2Id AND m.receiverId = :user1Id) " +
           "ORDER BY m.timestamp ASC")
    List<ChatMessage> findPrivateMessages(@Param("user1Id") Long user1Id, 
                                          @Param("user2Id") Long user2Id);
    
    @Query("SELECT m FROM ChatMessage m WHERE m.roomId = :roomId AND m.id > :lastMessageId ORDER BY m.timestamp ASC")
    List<ChatMessage> findNewMessages(@Param("roomId") String roomId, 
                                      @Param("lastMessageId") Long lastMessageId);
}
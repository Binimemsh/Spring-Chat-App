package com.chat.dto;

import com.chat.model.ChatMessage;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {
    private Long id;
    private ChatMessage.MessageType type;
    private String content;
    private String sender;
    private Long senderId;
    private Long receiverId;
    private String roomId;
    private Boolean read;
    private LocalDateTime timestamp;
    
    public static ChatMessageDTO fromEntity(ChatMessage message) {
        return new ChatMessageDTO(
            message.getId(),
            message.getType(),
            message.getContent(),
            message.getSender().getUsername(),
            message.getSender().getId(),
            message.getReceiverId(),
            message.getRoomId(),
            message.getRead(),
            message.getTimestamp()
        );
    }
}
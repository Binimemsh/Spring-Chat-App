package com.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class TypingNotificationDTO {
    private Long senderId;
    private Long receiverId; // null for public chat
    private boolean isTyping;
    private String senderUsername;
}
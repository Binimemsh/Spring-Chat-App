package com.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReadReceiptDTO {
    private Long messageId;
    private Long readByUserId;
    private String readByUsername;
    private LocalDateTime readAt;
}
package com.trabalhaki.backend.dto.chat;

import com.trabalhaki.backend.domain.enums.Role;

import java.time.LocalDateTime;
import java.util.List;

public record ConversationResponse(
        Long id,
        Long matchId,
        List<ChatMessageResponse> messages
) {
    public record ChatMessageResponse(
            Long id,
            Long senderId,
            String senderName,
            String content,
            LocalDateTime createdAt,
            Role senderRole
    ) {}
}

package com.unimove.domain.chat.dto;

import com.unimove.domain.chat.ChatMessage;
import com.unimove.domain.user.Role;

import java.time.Instant;
import java.util.UUID;

public record ChatMessageResponse(
        UUID id,
        Long seq,
        UUID rideId,
        UUID senderId,
        Role senderRole,
        String body,
        Instant createdAt
) {
    public static ChatMessageResponse from(ChatMessage m) {
        return new ChatMessageResponse(
                m.getId(),
                m.getSeq(),
                m.getRideId(),
                m.getSenderId(),
                m.getSenderRole(),
                m.getBody(),
                m.getCreatedAt()
        );
    }
}

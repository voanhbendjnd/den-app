package com.denhub.service.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

/**
 * DTO trả về thông tin một Conversation 1-1 (Direct Message).
 * Dùng cho GET /api/v1/conversations
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ConversationResponseDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    Long conversationId;
    String type;

    /** Thông tin người kia (không phải current user) */
    OtherUser otherUser;

    /** Tin nhắn mới nhất trong conversation */
    LastMessage lastMessage;

    Instant createdDate;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OtherUser {
        Long userId;
        String username;
        String name;
        String avatar;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LastMessage {
        Long messageId;
        String type;
        String content;
        String mediaUrl;
        Long senderId;
        Instant createdAt;
    }
}

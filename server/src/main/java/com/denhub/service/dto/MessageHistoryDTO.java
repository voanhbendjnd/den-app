package com.denhub.service.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.List;

/**
 * DTO trả về một tin nhắn trong lịch sử hội thoại.
 * Dùng cho GET /api/v1/conversations/{conversationId}/messages
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class MessageHistoryDTO implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    Long messageId;
    Long conversationId;

    /** Thông tin người gửi */
    SenderInfo sender;

    /** TEXT | IMAGE | FILE | SYSTEM */
    String type;

    String content;
    String mediaUrl;
    List<String> mediaUrls;

    /** Metadata cho file/ảnh */
    FileMetadata metadata;

    String status;
    Instant createdAt;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SenderInfo {
        Long userId;
        String name;
        String avatar;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FileMetadata {
        String fileName;
        Long fileSize;
        Integer width;
        Integer height;
    }
}

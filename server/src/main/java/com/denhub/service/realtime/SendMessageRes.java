package com.denhub.service.realtime;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageRes implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long messageId;
    private Long conversationId;
    private Sender sender;
    private ReplyToMessage replyToMessage;
    private Metadata metadata;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Sender {
        private Long userId;
        private String name;
        private String avatar;
    }

    private String type;
    private String content;
    private String mediaUrl;
    private List<String> mediaUrls;

    @Setter
    @Getter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReplyToMessage {
        private Long messageId;
        private String senderName;
        private String content;
    }

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        private int width;
        private int height;
        private long fileSize;
        private String fileName;
    }

    private String status;
    private Instant createdAt;
}


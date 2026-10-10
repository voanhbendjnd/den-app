package com.denhub.service.realtime;

import lombok.*;

import java.io.Serial;
import java.io.Serializable;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SendMessageReq implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;
    private Long conversationId;
    private Long targetUserId;
    // ("TEXT", "IMAGE", "AUDIO", "FILE", "SYSTEM")
    private String type;
    private String content;
    private String mediaUrl;
    private List<String> mediaUrls;
    private Long replyToMessageId;
    private Metadata metadata;

    @Builder
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Metadata {
        private int width;
        private int height;
        private long fileSize;
        private String fileName;
    }
}


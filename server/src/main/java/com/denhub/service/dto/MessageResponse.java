package com.denhub.service.dto;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long channelId,
        Long senderId,
        String content,
        Instant createdAt
) {
}

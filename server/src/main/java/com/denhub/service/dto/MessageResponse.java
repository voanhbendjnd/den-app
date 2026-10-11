package com.denhub.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;

import java.time.Instant;

public record MessageResponse(
        Long id,
        Long channelId,
        Long senderId,
        String content,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        Instant createdAt
) {
}

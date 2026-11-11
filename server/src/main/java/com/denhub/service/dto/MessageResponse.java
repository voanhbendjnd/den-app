package com.denhub.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Dữ liệu phản hồi tin nhắn")
public class MessageResponse {

    @Schema(description = "ID của tin nhắn", example = "101")
    Long id;

    @Schema(description = "ID của Channel chứa tin nhắn", example = "5")
    Long channelId;

    @Schema(description = "ID của người gửi", example = "2")
    Long senderId;

    @Schema(description = "Nội dung tin nhắn văn bản", example = "Hello mọi người!")
    String content;

    @Schema(description = "Thời điểm gửi", example = "2026-10-12T07:30:00Z")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant createdAt;

    @Schema(description = "Thời điểm cập nhật cuối", example = "2026-10-12T07:35:00Z")
    @JsonFormat(shape = JsonFormat.Shape.STRING)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    Instant updatedAt;

    public MessageResponse(Long id, Long channelId, Long senderId, String content, Instant createdAt) {
        this.id = id;
        this.channelId = channelId;
        this.senderId = senderId;
        this.content = content;
        this.createdAt = createdAt;
        this.updatedAt = null;
    }

    // Backward-compatible record-like accessors
    public Long id() {
        return this.id;
    }

    public Long channelId() {
        return this.channelId;
    }

    public Long senderId() {
        return this.senderId;
    }

    public String content() {
        return this.content;
    }

    public Instant createdAt() {
        return this.createdAt;
    }

    public Instant updatedAt() {
        return this.updatedAt;
    }
}

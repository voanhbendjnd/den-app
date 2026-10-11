package com.denhub.service.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
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
public class MessageResponse {

    Long id;
    Long channelId;
    Long senderId;
    String content;

    @JsonFormat(shape = JsonFormat.Shape.STRING)
    Instant createdAt;

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
}

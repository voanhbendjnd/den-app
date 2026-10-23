package com.denhub.service.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DisplayPostDTO {

    Long id;
    Long userId;
    String authorName;
    String authorUsername;
    String authorAvatar;
    String content;
    // public, FRIENDS_ONLY, PRIVATE
    String privacy;
    Integer likeCounts;
    String mediaUrl;
    Instant createdDate;
}

package com.denhub.service.realtime;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.List;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class FeedPostRes {
    Long postId;
    String content;
    String mediaUrl;
    List<String> mediaUrls;
    User user;
    Instant createdAt;
    Instant lastModifiedDate;
    String privacy;
    Integer likeCounts;
    @Getter
    @Setter
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class User{
        Long id;
        String name;
        String username;
        String avatar;
    }
}

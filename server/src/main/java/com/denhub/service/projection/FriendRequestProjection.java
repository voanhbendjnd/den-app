package com.denhub.service.projection;

import java.time.Instant;

public interface FriendRequestProjection {
    Long getSenderId();
    String getUsername();
    String getName();
    String getAvatar();
    Instant getLastModifiedDate();
}

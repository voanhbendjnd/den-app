package com.denhub.service.realtime;

import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FriendshipAction {
    Long senderId;
    String username;
    String name;
    String avatar;
    String lastModifiedDate;
    String event;
    String status;
}

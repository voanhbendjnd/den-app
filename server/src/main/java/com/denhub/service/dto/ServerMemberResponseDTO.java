package com.denhub.service.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServerMemberResponseDTO {
    Long id;
    Long serverId;
    Long userId;
    String username;
    String name;
    String nickname;
    String avatar;
    String role;
}

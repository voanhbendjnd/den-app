package com.denhub.service.dto;

import com.denhub.domain.Authority;
import com.denhub.domain.User;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminUserResponseDTO {

    Long id;
    String email;
    String name;
    String username;
    String avatar;
    String type;
    Boolean activated;
    String role;
    Set<String> authorities;
    long ownedServerCount;
    Instant createdDate;
    Instant lastModifiedDate;

    public static AdminUserResponseDTO fromUser(User user, long ownedServerCount) {
        if (user == null) {
            return null;
        }
        Set<String> authNames = user.getAuthorities() != null
                ? user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toSet())
                : Set.of();
        String primaryRole = authNames.contains("ROLE_ADMIN") ? "ROLE_ADMIN" : "ROLE_USER";

        return AdminUserResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .type(user.getType() != null ? user.getType() : "SYSTEM")
                .activated(Boolean.TRUE.equals(user.getActivated()))
                .role(primaryRole)
                .authorities(authNames)
                .ownedServerCount(ownedServerCount)
                .createdDate(user.getCreatedDate())
                .lastModifiedDate(user.getLastModifiedDate())
                .build();
    }
}


package com.denhub.service.dto;

import com.denhub.domain.Authority;
import com.denhub.domain.User;
import lombok.*;
import lombok.experimental.FieldDefaults;

import java.time.Instant;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * DTO đại diện cho thông tin hồ sơ người dùng cá nhân (User Profile).
 * Loại bỏ các thông tin nhạy cảm như password_hash, reset_key, refresh_token.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UserProfileResponseDTO {

    Long id;
    String email;
    String name;
    String username;
    String avatar;
    Boolean activated;
    Instant createdDate;
    Set<String> authorities;

    public static UserProfileResponseDTO fromUser(User user) {
        if (user == null) {
            return null;
        }

        Set<String> authNames = user.getAuthorities() != null
                ? user.getAuthorities().stream().map(Authority::getName).collect(Collectors.toSet())
                : Set.of();

        return UserProfileResponseDTO.builder()
                .id(user.getId())
                .email(user.getEmail())
                .name(user.getName())
                .username(user.getUsername())
                .avatar(user.getAvatar())
                .activated(user.getActivated())
                .createdDate(user.getCreatedDate())
                .authorities(authNames)
                .build();
    }
}

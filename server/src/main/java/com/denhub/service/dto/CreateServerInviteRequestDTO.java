package com.denhub.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
@Schema(description = "Request tạo mã mời Server tùy chọn hạn dùng")
public class CreateServerInviteRequestDTO {

    @Schema(description = "Thời điểm hết hạn (null = vĩnh viễn)", example = "2026-10-21T14:00:00Z", nullable = true)
    Instant expiresAt;
}

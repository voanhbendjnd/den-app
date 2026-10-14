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
@Schema(description = "Response thông tin mã mời Server")
public class ServerInviteResponseDTO {

    @Schema(description = "ID mã mời", example = "1")
    Long id;

    @Schema(description = "ID Server", example = "10")
    Long serverId;

    @Schema(description = "Mã mời duy nhất", example = "aB7kX9mQ")
    String code;

    @Schema(description = "ID người tạo mã mời", example = "2")
    Long createdBy;

    @Schema(description = "Thời điểm hết hạn (null = vĩnh viễn)", example = "2026-10-21T14:00:00Z", nullable = true)
    Instant expiresAt;

    @Schema(description = "Trạng thái mã mời", example = "ACTIVE")
    String status;

    @Schema(description = "Thời điểm tạo mã mời", example = "2026-10-14T14:00:00Z")
    Instant createdAt;
}

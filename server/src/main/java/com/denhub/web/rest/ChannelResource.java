package com.denhub.web.rest;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.denhub.service.ChannelService;
import com.denhub.service.dto.ChannelResponseDTO;
import com.denhub.service.dto.CreateChannelDTO;
import com.denhub.service.dto.UpdateChannelDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Tag(name = "Channel", description = "API quản lý Channel trong Server")
@SecurityRequirement(name = "bearerAuth")
public class ChannelResource {

    ChannelService channelService;

    @PostMapping("/channels")
    @Operation(summary = "Tạo mới một Channel", description = "Tạo một kênh chat mới trong server. Yêu cầu đăng nhập và là thành viên/chủ sở hữu server.")
    public ResponseEntity<ChannelResponseDTO> createChannel(@Valid @RequestBody CreateChannelDTO dto) {
        ChannelResponseDTO response = channelService.createChannel(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/servers/{serverId}/channels")
    @Operation(summary = "Lấy danh sách Channel của một Server", description = "Lấy toàn bộ channel thuộc server chỉ định theo position tăng dần. Yêu cầu đăng nhập và là thành viên/chủ sở hữu server.")
    public ResponseEntity<java.util.List<ChannelResponseDTO>> getChannelsByServerId(@PathVariable("serverId") Long serverId) {
        java.util.List<ChannelResponseDTO> channels = channelService.getChannelsByServerId(serverId);
        return ResponseEntity.ok(channels);
    }

    @PutMapping("/channels/{channelId}")
    @Operation(summary = "Cập nhật thông tin Channel", description = "Cập nhật tên và vị trí của kênh chat. Yêu cầu đăng nhập và là thành viên/chủ sở hữu server.")
    public ResponseEntity<ChannelResponseDTO> updateChannel(
            @PathVariable("channelId") Long channelId,
            @Valid @RequestBody UpdateChannelDTO dto
    ) {
        ChannelResponseDTO response = channelService.updateChannel(channelId, dto);
        return ResponseEntity.ok(response);
    }
}

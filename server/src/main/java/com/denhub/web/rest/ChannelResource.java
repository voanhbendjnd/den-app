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
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/channels")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Tag(name = "Channel", description = "API quản lý Channel trong Server")
@SecurityRequirement(name = "bearerAuth")
public class ChannelResource {

    ChannelService channelService;

    @PostMapping
    @Operation(summary = "Tạo mới một Channel", description = "Tạo một kênh chat mới trong server. Yêu cầu đăng nhập và là thành viên/chủ sở hữu server.")
    public ResponseEntity<ChannelResponseDTO> createChannel(@Valid @RequestBody CreateChannelDTO dto) {
        ChannelResponseDTO response = channelService.createChannel(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

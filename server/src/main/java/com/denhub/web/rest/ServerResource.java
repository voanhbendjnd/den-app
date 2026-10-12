package com.denhub.web.rest;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import com.denhub.service.ServerService;
import com.denhub.service.dto.CreateServerDTO;
import com.denhub.service.dto.ServerResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/servers")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Tag(name = "Server", description = "API quản lý Server / Room")
@SecurityRequirement(name = "bearerAuth")
public class ServerResource {

    ServerService serverService;

    @PostMapping
    @Operation(summary = "Tạo mới một Server", description = "Tạo một không gian giao tiếp mới. Yêu cầu đăng nhập.")
    public ResponseEntity<ServerResponseDTO> createServer(@Valid @RequestBody CreateServerDTO dto) {
        ServerResponseDTO response = serverService.createServer(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @Operation(summary = "Lấy danh sách Server", description = "Lấy danh sách các Server mà người dùng hiện tại đang tham gia hoặc sở hữu. Yêu cầu đăng nhập.")
    public ResponseEntity<java.util.List<ServerResponseDTO>> getUserServers() {
        java.util.List<ServerResponseDTO> servers = serverService.getUserServers();
        return ResponseEntity.ok(servers);
    }

    @PostMapping("/{serverId}/join")
    @Operation(summary = "Tham gia vào một Server", description = "Cho phép người dùng hiện tại tham gia vào Server thông qua ID. Yêu cầu đăng nhập.")
    public ResponseEntity<Void> joinServer(@PathVariable Long serverId) {
        serverService.joinServer(serverId);
        return ResponseEntity.noContent().build();
    }
}

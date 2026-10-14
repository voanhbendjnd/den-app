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
import com.denhub.service.dto.CreateServerInviteRequestDTO;
import com.denhub.service.dto.ServerInviteResponseDTO;
import com.denhub.service.dto.ServerMemberResponseDTO;
import com.denhub.service.dto.ServerResponseDTO;
import com.denhub.util.anotation.ApiMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import java.util.List;

@RestController
@RequestMapping("/api/v1/servers")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Tag(name = "Server", description = "API quản lý Server / Room")
@SecurityRequirement(name = "bearerAuth")
public class ServerResource {

    ServerService serverService;

    @GetMapping("/{serverId}")
    @Operation(summary = "Đọc chi tiết Server", description = "Lấy thông tin Server. Yêu cầu đăng nhập và là chủ sở hữu hoặc thành viên của Server.")
    public ResponseEntity<ServerResponseDTO> getServerDetail(@PathVariable("serverId") Long serverId) {
        return ResponseEntity.ok(serverService.getServerDetail(serverId));
    }

    @GetMapping("/{serverId}/members")
    @Operation(summary = "Lấy danh sách thành viên của Server", description = "Lấy danh sách thành viên thuộc Server chỉ định. Yêu cầu đăng nhập và là thành viên hoặc chủ sở hữu Server.")
    public ResponseEntity<List<ServerMemberResponseDTO>> getServerMembers(@PathVariable("serverId") Long serverId) {
        return ResponseEntity.ok(serverService.getServerMembers(serverId));
    }

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

    @PostMapping("/{serverId}/invites")
    @ApiMessage("Tạo mã mời thành công")
    @Operation(summary = "Tạo mã mời tham gia Server", description = "Tạo mã mời mới cho Server. Yêu cầu đăng nhập và chỉ Server Owner mới có quyền tạo.")
    public ResponseEntity<ServerInviteResponseDTO> createServerInvite(
            @PathVariable("serverId") Long serverId,
            @RequestBody(required = false) @Valid CreateServerInviteRequestDTO dto) {
        ServerInviteResponseDTO response = serverService.createServerInvite(serverId, dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/join-invite/{code}")
    @ApiMessage("Tham gia Server bằng mã mời thành công")
    @Operation(summary = "Tham gia Server bằng mã mời", description = "Cho phép người dùng tham gia vào Server bằng mã mời 8 ký tự hợp lệ. Yêu cầu đăng nhập.")
    public ResponseEntity<ServerResponseDTO> joinServerByInviteCode(@PathVariable("code") String code) {
        ServerResponseDTO response = serverService.joinServerByInviteCode(code);
        return ResponseEntity.ok(response);
    }
}

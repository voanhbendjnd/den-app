package com.denhub.web.rest;

import com.denhub.security.AuthoritiesConstants;
import com.denhub.service.AdminUserService;
import com.denhub.service.dto.AdminCreateUserDTO;
import com.denhub.service.dto.AdminOwnershipPreviewDTO;
import com.denhub.service.dto.AdminSoftDeleteResultDTO;
import com.denhub.service.dto.AdminUpdateUserDTO;
import com.denhub.service.dto.AdminUserPageDTO;
import com.denhub.service.dto.AdminUserResponseDTO;
import com.denhub.util.anotation.ApiMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('" + AuthoritiesConstants.ADMIN + "')")
@Tag(name = "Admin User Management", description = "API Quản trị tài khoản người dùng dành riêng cho Admin")
@SecurityRequirement(name = "bearerAuth")
public class AdminUserResource {

    AdminUserService adminUserService;

    @GetMapping
    @ApiMessage("Lấy danh sách tài khoản thành công")
    @Operation(summary = "Danh sách tài khoản", description = "Phân trang, tìm kiếm theo tên/email/username và lọc theo vai trò/trạng thái.")
    public ResponseEntity<AdminUserPageDTO> getUsers(
            @RequestParam(name = "search", required = false) String search,
            @RequestParam(name = "role", required = false) String role,
            @RequestParam(name = "activated", required = false) Boolean activated,
            @RequestParam(name = "page", defaultValue = "0") int page,
            @RequestParam(name = "size", defaultValue = "10") int size) {
        return ResponseEntity.ok(adminUserService.getUsers(search, role, activated, page, size));
    }

    @GetMapping("/{userId}")
    @ApiMessage("Lấy thông tin tài khoản thành công")
    @Operation(summary = "Chi tiết tài khoản", description = "Xem thông tin chi tiết một tài khoản người dùng.")
    public ResponseEntity<AdminUserResponseDTO> getUserDetail(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(adminUserService.getUserDetail(userId));
    }

    @GetMapping("/{userId}/ownership-preview")
    @ApiMessage("Kiểm tra quyền sở hữu Server thành công")
    @Operation(summary = "Kiểm tra Server đang làm chủ trước khi xóa mềm", description = "Trả về danh sách các Server người dùng đang sở hữu và phương án tự động chuyển quyền hoặc giải tán Server trống.")
    public ResponseEntity<AdminOwnershipPreviewDTO> getOwnershipPreview(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(adminUserService.getOwnershipPreview(userId));
    }

    @PostMapping
    @ApiMessage("Tạo tài khoản mới thành công")
    @Operation(summary = "Tạo tài khoản mới", description = "Admin tạo tài khoản người dùng hoặc quản trị viên mới.")
    public ResponseEntity<AdminUserResponseDTO> createUser(@Valid @RequestBody AdminCreateUserDTO dto) {
        AdminUserResponseDTO created = adminUserService.createUser(dto);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{userId}")
    @ApiMessage("Cập nhật tài khoản thành công")
    @Operation(summary = "Cập nhật thông tin tài khoản", description = "Admin cập nhật thông tin, quyền hạn hoặc trạng thái tài khoản.")
    public ResponseEntity<AdminUserResponseDTO> updateUser(
            @PathVariable("userId") Long userId,
            @Valid @RequestBody AdminUpdateUserDTO dto) {
        return ResponseEntity.ok(adminUserService.updateUser(userId, dto));
    }

    @DeleteMapping("/{userId}")
    @ApiMessage("Xóa mềm tài khoản và xử lý quyền chủ Server thành công")
    @Operation(summary = "Xóa mềm tài khoản", description = "Vô hiệu hóa tài khoản, thu hồi phiên đăng nhập, tự động chuyển quyền chủ Server cho thành viên gia nhập sớm nhất đang hoạt động hoặc tự động xóa Server trống.")
    public ResponseEntity<AdminSoftDeleteResultDTO> softDeleteUser(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(adminUserService.softDeleteUser(userId));
    }

    @PatchMapping("/{userId}/restore")
    @ApiMessage("Khôi phục tài khoản thành công")
    @Operation(summary = "Khôi phục tài khoản", description = "Kích hoạt lại tài khoản đã bị xóa mềm.")
    public ResponseEntity<AdminUserResponseDTO> restoreUser(@PathVariable("userId") Long userId) {
        return ResponseEntity.ok(adminUserService.restoreUser(userId));
    }
}


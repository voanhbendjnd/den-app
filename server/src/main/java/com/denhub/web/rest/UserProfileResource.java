package com.denhub.web.rest;

import jakarta.validation.Valid;
import lombok.AccessLevel;
import lombok.RequiredArgsConstructor;
import lombok.experimental.FieldDefaults;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.denhub.service.UserService;
import com.denhub.service.dto.ChangePasswordDTO;
import com.denhub.service.dto.UpdateUserProfileDTO;
import com.denhub.service.dto.UserProfileResponseDTO;
import com.denhub.util.anotation.ApiMessage;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * REST Controller quản lý thông tin hồ sơ người dùng cá nhân (User Profile).
 */
@RestController
@RequestMapping("/api/v1/users")
@FieldDefaults(level = AccessLevel.PRIVATE, makeFinal = true)
@RequiredArgsConstructor
@Tag(name = "User Profile", description = "API quản lý hồ sơ người dùng cá nhân")
@SecurityRequirement(name = "bearerAuth")
public class UserProfileResource {

    UserService userService;

    /**
     * {@code GET  /api/v1/users/profile} : Xem thông tin hồ sơ cá nhân của người dùng hiện tại.
     *
     * @return {@link ResponseEntity} với status {@code 200 (OK)} và body là {@link UserProfileResponseDTO}.
     */
    @GetMapping("/profile")
    @Operation(summary = "Xem hồ sơ cá nhân", description = "Lấy toàn bộ thông tin hồ sơ của người dùng đang đăng nhập.")
    @ApiMessage("Lấy thông tin hồ sơ cá nhân thành công")
    public ResponseEntity<UserProfileResponseDTO> getUserProfile() {
        UserProfileResponseDTO profile = userService.getUserProfile();
        return ResponseEntity.ok(profile);
    }

    /**
     * {@code GET  /api/v1/users/me} : Endpoint tương thích (alias) để lấy thông tin hồ sơ người dùng hiện tại.
     *
     * @return {@link ResponseEntity} với status {@code 200 (OK)} và body là {@link UserProfileResponseDTO}.
     */
    @GetMapping("/me")
    @Operation(summary = "Xem hồ sơ cá nhân (alias)", description = "Lấy thông tin hồ sơ cá nhân của người dùng đang đăng nhập.")
    @ApiMessage("Lấy thông tin hồ sơ cá nhân thành công")
    public ResponseEntity<UserProfileResponseDTO> getUserProfileMe() {
        UserProfileResponseDTO profile = userService.getUserProfile();
        return ResponseEntity.ok(profile);
    }

    /**
     * {@code PUT  /api/v1/users/profile} : Cập nhật thông tin hồ sơ cá nhân của người dùng hiện tại.
     *
     * @param dto DTO chứa thông tin cập nhật (name, username, avatar).
     * @return {@link ResponseEntity} với status {@code 200 (OK)} và body là {@link UserProfileResponseDTO}.
     */
    @PutMapping("/profile")
    @Operation(summary = "Cập nhật hồ sơ cá nhân", description = "Cập nhật tên hiển thị, username, hoặc avatar của người dùng đang đăng nhập.")
    @ApiMessage("Cập nhật thông tin hồ sơ cá nhân thành công")
    public ResponseEntity<UserProfileResponseDTO> updateUserProfile(@Valid @RequestBody UpdateUserProfileDTO dto) {
        UserProfileResponseDTO updatedProfile = userService.updateUserProfile(dto);
        return ResponseEntity.ok(updatedProfile);
    }

    /**
     * {@code PUT  /api/v1/users/me} : Endpoint tương thích (alias) để cập nhật thông tin hồ sơ người dùng hiện tại.
     *
     * @param dto DTO chứa thông tin cập nhật (name, username, avatar).
     * @return {@link ResponseEntity} với status {@code 200 (OK)} và body là {@link UserProfileResponseDTO}.
     */
    @PutMapping("/me")
    @Operation(summary = "Cập nhật hồ sơ cá nhân (alias)", description = "Cập nhật thông tin hồ sơ cá nhân của người dùng đang đăng nhập.")
    @ApiMessage("Cập nhật thông tin hồ sơ cá nhân thành công")
    public ResponseEntity<UserProfileResponseDTO> updateUserProfileMe(@Valid @RequestBody UpdateUserProfileDTO dto) {
        UserProfileResponseDTO updatedProfile = userService.updateUserProfile(dto);
        return ResponseEntity.ok(updatedProfile);
    }

    /**
     * {@code POST  /api/v1/users/change-password} : Thay đổi mật khẩu người dùng hiện tại.
     *
     * @param dto DTO chứa mật khẩu hiện tại và mật khẩu mới.
     * @return {@link ResponseEntity} với status {@code 200 (OK)}.
     */
    @PostMapping("/change-password")
    @Operation(summary = "Đổi mật khẩu", description = "Thay đổi mật khẩu đăng nhập của người dùng hiện tại.")
    @ApiMessage("Đổi mật khẩu thành công")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return ResponseEntity.ok().build();
    }

    /**
     * {@code POST  /api/v1/users/password} : Endpoint tương thích (alias) để đổi mật khẩu.
     *
     * @param dto DTO chứa mật khẩu hiện tại và mật khẩu mới.
     * @return {@link ResponseEntity} với status {@code 200 (OK)}.
     */
    @PostMapping("/password")
    @Operation(summary = "Đổi mật khẩu (alias)", description = "Thay đổi mật khẩu đăng nhập của người dùng hiện tại.")
    @ApiMessage("Đổi mật khẩu thành công")
    public ResponseEntity<Void> changePasswordAlias(@Valid @RequestBody ChangePasswordDTO dto) {
        userService.changePassword(dto);
        return ResponseEntity.ok().build();
    }
}

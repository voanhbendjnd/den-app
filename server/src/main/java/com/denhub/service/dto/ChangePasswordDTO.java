package com.denhub.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * DTO nhận dữ liệu yêu cầu đổi mật khẩu của người dùng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChangePasswordDTO {

    @NotBlank(message = "Mật khẩu hiện tại không được để trống")
    String currentPassword;

    @NotBlank(message = "Mật khẩu mới không được để trống")
    @Size(min = 4, max = 100, message = "Mật khẩu mới phải từ 4 đến 100 ký tự")
    String newPassword;
}

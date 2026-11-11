package com.denhub.service.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

/**
 * DTO yêu cầu cập nhật thông tin hồ sơ cá nhân của người dùng.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateUserProfileDTO {

    @Size(max = 50, message = "Tên hiển thị không được vượt quá 50 ký tự")
    String name;

    @Pattern(
        regexp = "^[a-zA-Z0-9._-]{3,50}$",
        message = "Tên người dùng (username) phải gồm 3-50 ký tự chữ cái, chữ số, dấu gạch dưới, gạch ngang hoặc dấu chấm"
    )
    String username;

    @Size(max = 500, message = "Đường dẫn ảnh đại diện không được vượt quá 500 ký tự")
    String avatar;
}

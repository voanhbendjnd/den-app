package com.denhub.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminUpdateUserDTO {

    @Email(message = "Email không đúng định dạng")
    @Size(min = 5, max = 254, message = "Email phải từ 5 đến 254 ký tự")
    String email;

    @Size(max = 50, message = "Tên hiển thị tối đa 50 ký tự")
    String name;

    @Size(max = 100, message = "Username tối đa 100 ký tự")
    String username;

    String avatar;

    @Size(min = 6, max = 100, message = "Mật khẩu mới phải từ 6 đến 100 ký tự")
    String password;

    String role;

    Boolean activated;
}


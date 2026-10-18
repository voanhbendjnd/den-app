package com.denhub.service.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class RegisterReqDTO {

    @NotBlank(message = "Email không được để trống")
    @Email(message = "Email không đúng định dạng")
    @Size(min = 5, max = 254, message = "Email từ 5 đến 254 ký tự")
    String email;

    @NotBlank(message = "Mật khẩu không được để trống")
    @Size(min = 4, max = 100, message = "Mật khẩu phải từ 4 đến 100 ký tự")
    String password;

    @Size(max = 50, message = "Tên hiển thị tối đa 50 ký tự")
    String name;

    @Size(max = 50, message = "Tên đăng nhập tối đa 50 ký tự")
    String username;
}

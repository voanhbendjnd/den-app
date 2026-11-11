package com.denhub.service.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
@ToString
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateMessageRequest {

    @Schema(description = "Nội dung tin nhắn văn bản cập nhật (tối đa 4000 ký tự UTF-16)", example = "Nội dung tin nhắn đã được chỉnh sửa!")
    @NotBlank(message = "Nội dung tin nhắn không được để trống")
    // Require a non-whitespace character, including Unicode whitespace handling.
    @Pattern(regexp = "(?sU).*\\S.*", message = "Nội dung tin nhắn không được để trống")
    @Size(max = 4000, message = "Nội dung tin nhắn không được vượt quá 4000 kí tự")
    String content;

    // Backward-compatible record-like accessor
    public String content() {
        return this.content;
    }
}

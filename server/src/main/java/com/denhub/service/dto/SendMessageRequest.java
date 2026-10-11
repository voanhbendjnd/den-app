package com.denhub.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SendMessageRequest(
                @NotBlank(message = "Nội dung tin nhắn không được để trống")
                // Require a non-whitespace character, including Unicode whitespace handling.
                @Pattern(regexp = "(?sU).*\\S.*", message = "Nội dung tin nhắn không được để trống") @Size(max = 4000, message = "Nội dung tin nhắn không được vượt quá 4000 kí tự") String content) {
}

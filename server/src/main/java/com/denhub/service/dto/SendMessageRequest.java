package com.denhub.service.dto;

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
public class SendMessageRequest {

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

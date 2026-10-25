package com.denhub.service.dto;

import jakarta.validation.constraints.Size;
import lombok.*;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdatePostDTO {
    @Size(max = 120, message = "Content cannot exceed 120 characters")
    String content;

    String privacy;

    @Size(max = 1000, message = "Media URL cannot exceed 1000 characters")
    String mediaUrl;
}

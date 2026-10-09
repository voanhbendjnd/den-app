package com.denhub.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateServerDTO {

    @NotBlank(message = "Server name must not be empty")
    @Size(min = 1, max = 20, message = "Server name must be between 1 and 20 characters")
    String name;

    @Size(max = 255, message = "Icon URL must not exceed 255 characters")
    String iconUrl;
}

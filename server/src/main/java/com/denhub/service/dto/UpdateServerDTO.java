package com.denhub.service.dto;

import jakarta.validation.constraints.NotBlank;
import com.denhub.domain.enums.ServerVisibility;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateServerDTO {

    @NotBlank(message = "Server name must not be empty")
    @Pattern(regexp = "(?sU).*\\S.*", message = "Server name must not be blank")
    @Size(min = 1, max = 100, message = "Server name must be between 1 and 100 characters")
    String name;

    @Size(max = 255, message = "Icon URL must not exceed 255 characters")
    String iconUrl;

    // Omitted by older clients: preserve the existing visibility.
    ServerVisibility visibility;
}

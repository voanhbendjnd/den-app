package com.denhub.service.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class UpdateChannelDTO {

    @NotBlank(message = "Channel name must not be empty")
    @Pattern(regexp = "(?sU).*\\S.*", message = "Channel name must not be blank")
    @Size(min = 2, max = 20, message = "Channel name must be between 2 and 20 characters")
    String name;

    @Min(value = 0, message = "Position must be non-negative")
    Integer position;
}

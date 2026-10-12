package com.denhub.service.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class CreateChannelDTO {

    @NotNull(message = "Server ID must not be null")
    Long serverId;

    @NotBlank(message = "Channel name must not be empty")
    @Size(min = 2, max = 20, message = "Channel name must be between 2 and 20 characters")
    String name;
}

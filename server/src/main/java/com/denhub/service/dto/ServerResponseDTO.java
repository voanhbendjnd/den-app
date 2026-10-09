package com.denhub.service.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ServerResponseDTO {
    Long id;
    String name;
    String iconUrl;
    Long ownerId;
    Instant createdDate;
}

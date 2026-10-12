package com.denhub.service.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.FieldDefaults;

import java.time.Instant;

@Getter
@Setter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class ChannelResponseDTO {
    Long id;
    Long serverId;
    String name;
    int position;
    Instant createdDate;
}

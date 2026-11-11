package com.denhub.service.dto;

import lombok.*;
import lombok.experimental.FieldDefaults;

import java.util.List;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@FieldDefaults(level = AccessLevel.PRIVATE)
public class AdminUserPageDTO {

    List<AdminUserResponseDTO> items;
    int page;
    int size;
    long totalElements;
    int totalPages;
}


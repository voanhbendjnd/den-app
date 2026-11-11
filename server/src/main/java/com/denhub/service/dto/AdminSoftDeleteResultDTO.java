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
public class AdminSoftDeleteResultDTO {

    AdminUserResponseDTO user;
    int transferredServerCount;
    int deletedEmptyServerCount;
    List<ServerResolutionItem> serverResolutions;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ServerResolutionItem {
        Long serverId;
        String serverName;
        String action; // TRANSFERRED or DELETED_EMPTY
        Long newOwnerId;
        String newOwnerUsername;
        String newOwnerName;
    }
}


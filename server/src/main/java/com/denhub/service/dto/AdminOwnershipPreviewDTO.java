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
public class AdminOwnershipPreviewDTO {

    Long userId;
    String email;
    String username;
    String name;
    int totalOwnedServers;
    int transferableCount;
    int autoDeleteCount;
    List<ServerActionPreview> servers;

    @Getter
    @Setter
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class ServerActionPreview {
        Long serverId;
        String serverName;
        long activeMemberCount;
        String action; // TRANSFER_OWNERSHIP or DELETE_EMPTY_SERVER
        Long nextOwnerId;
        String nextOwnerUsername;
        String nextOwnerName;
        String nextOwnerEmail;
    }
}


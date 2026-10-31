package com.denhub.service.dto;

import java.util.List;

public record ServerDiscoveryDTO(List<Item> items, int page, int size, long totalElements, int totalPages) {
    public record Item(Long id, String name, String iconUrl, boolean joined) {}
}

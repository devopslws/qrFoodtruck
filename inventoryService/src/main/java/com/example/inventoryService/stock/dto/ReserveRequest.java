package com.example.inventoryService.stock.dto;

import java.util.List;

public record ReserveRequest(List<Item> items) {
    public record Item(Long productId, int quantity) {}
}
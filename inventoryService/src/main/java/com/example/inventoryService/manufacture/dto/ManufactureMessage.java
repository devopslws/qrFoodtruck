package com.example.inventoryService.manufacture.dto;

import java.util.List;

/**
 * order-service로부터 수신하는 제조 지시 메시지
 */
public record ManufactureMessage(
        String orderNo,
        String sessionUuid,
        List<Item> items
) {
    public record Item(Long productId, String productName, int quantity) {}
}
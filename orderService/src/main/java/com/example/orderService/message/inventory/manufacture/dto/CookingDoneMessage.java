package com.example.orderService.message.inventory.manufacture.dto;

import java.util.List;

/**
 * order-service → inventory-service 제조 지시 메시지
 */
public record CookingDoneMessage(
        String orderNo,
        String sessionUuid,
        String status
) {}
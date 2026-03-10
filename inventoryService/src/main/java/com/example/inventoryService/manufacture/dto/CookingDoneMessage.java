package com.example.inventoryService.manufacture.dto;

/**
 * inventory-service → order-service 조리완료 메시지
 */
public record CookingDoneMessage(
        String orderNo,
        String sessionUuid
) {}
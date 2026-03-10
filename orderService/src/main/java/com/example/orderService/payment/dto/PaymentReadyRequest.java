package com.example.orderService.payment.dto;

import java.util.List;

public record PaymentReadyRequest(
        String orderName,
        int totalPrice,
        List<Item> items
) {
    public record Item(Long productId, String productName, int quantity, int unitPrice) {}
}
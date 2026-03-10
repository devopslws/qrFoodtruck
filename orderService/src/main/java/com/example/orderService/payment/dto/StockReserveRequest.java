package com.example.orderService.payment.dto;

import java.util.List;

public record StockReserveRequest(List<Item> items) {
    public record Item(Long productId, int quantity) {}
}
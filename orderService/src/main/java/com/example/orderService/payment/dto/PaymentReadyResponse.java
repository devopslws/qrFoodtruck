package com.example.orderService.payment.dto;

public record PaymentReadyResponse(
        String clientKey,
        String orderNo,
        int amount,
        String orderName
) {}
package com.example.orderService.admin.dto;

public record RefundRequest(String orderNo, int cancelAmount, String cancelReason) {}

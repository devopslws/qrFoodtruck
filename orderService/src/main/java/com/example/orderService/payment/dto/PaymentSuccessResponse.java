package com.example.orderService.payment.dto;

public record PaymentSuccessResponse(boolean success, String orderNo, String reason) {}
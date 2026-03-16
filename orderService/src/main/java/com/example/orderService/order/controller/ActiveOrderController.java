package com.example.orderService.order.controller;

import com.example.orderService.order.service.OrderService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 복귀 고객의 진행중 주문 조회 — HTTP 바인딩 전담
 * GET /api/order/active?uuid=...
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/order")
public class ActiveOrderController {

    private final OrderService orderService;

    @GetMapping("/active")
    public ResponseEntity<List<Map<String, String>>> active(@RequestParam String uuid) {
        return ResponseEntity.ok(orderService.getActiveOrders(uuid));
    }
}


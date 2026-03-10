package com.example.orderService.admin;

import com.example.orderService.order.entity.Orders;
import com.example.orderService.order.entity.OrderItem;
import com.example.orderService.order.repository.OrderItemRepository;
import com.example.orderService.order.repository.OrdersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 오늘 판매 로그 조회
 * GET /api/admin/sales/log  — 오늘 PAID 이상 주문 목록 (최신순)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/sales")
public class AdminSalesLogController {

    private final OrdersRepository    ordersRepository;
    private final OrderItemRepository orderItemRepository;

    @GetMapping("/log")
    public ResponseEntity<List<Map<String, Object>>> log() {
        LocalDateTime startOfDay = LocalDate.now().atStartOfDay();
        LocalDateTime now        = LocalDateTime.now();

        // 오늘 결제 완료된 주문 (PAID, COOKING, READY, DELIVERED)
        List<Orders> orders = ordersRepository
                .findByStatusInAndCreatedAtBetweenOrderByCreatedAtDesc(
                        List.of("PAID", "COOKING", "READY", "DELIVERED", "CANCELLED"),
                        startOfDay, now
                );

        List<Map<String, Object>> result = orders.stream().map(order -> {
            List<OrderItem> items = orderItemRepository.findByOrder(order);
            String desc = items.isEmpty() ? "-"
                    : items.size() == 1 ? items.get(0).getProductName()
                    : items.get(0).getProductName() + " 외 " + (items.size() - 1) + "건";

            return Map.<String, Object>of(
                    "orderNo",    order.getOrderNo(),
                    "desc",       desc,
                    "status",     order.getStatus(),
                    "totalPrice", order.getTotalPrice(),
                    "createdAt",  order.getCreatedAt().toString()
            );
        }).collect(Collectors.toList());

        return ResponseEntity.ok(result);
    }
}
package com.example.orderService.order.controller;

import com.example.orderService.order.entity.Orders;
import com.example.orderService.order.entity.OrderItem;
import com.example.orderService.order.repository.OrdersRepository;
import com.example.orderService.order.repository.OrderItemRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 복귀 고객의 진행중 주문 조회
 * GET /api/order/active?uuid=...
 *
 * Toss 리다이렉트 후 화면 복구용.
 * PENDING(결제전), CANCELLED 제외 → PAID, COOKING 상태만 반환
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/order")
public class ActiveOrderController {

    private final OrdersRepository ordersRepository;
    private final OrderItemRepository orderItemRepository;

    @GetMapping("/active")
    public ResponseEntity<List<Map<String, String>>> active(@RequestParam String uuid) {
        List<Orders> activeOrders = ordersRepository
                .findBySessionUuidAndStatusIn(uuid, List.of("PAID", "COOKING", "READY"));

        List<Map<String, String>> result = activeOrders.stream().map(order -> {
            List<OrderItem> items = orderItemRepository.findByOrder(order);
            String desc = items.isEmpty() ? order.getOrderNo()
                    : items.size() == 1
                    ? items.get(0).getProductName()
                    : items.get(0).getProductName() + " 외 " + (items.size() - 1) + "건";

            return Map.of(
                    "orderNo", order.getOrderNo(),
                    "desc",    desc,
                    "status",  order.getStatus()
            );
        }).toList();

        log.info("[ActiveOrder] uuid={}, 진행중 주문 {}건", uuid, result.size());
        return ResponseEntity.ok(result);
    }
}


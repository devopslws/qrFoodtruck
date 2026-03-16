package com.example.orderService.order.service;

import com.example.orderService.order.entity.OrderItem;
import com.example.orderService.order.entity.Orders;
import com.example.orderService.order.repository.OrderItemRepository;
import com.example.orderService.order.repository.OrdersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrderService {

    private final OrdersRepository ordersRepository;
    private final OrderItemRepository orderItemRepository;

    /**
     * 복귀 고객의 진행중 주문 조회 (Toss 리다이렉트 후 화면 복구용)
     * PAID / COOKING / READY 상태 주문 반환
     */
    public List<Map<String, String>> getActiveOrders(String uuid) {
        List<Orders> activeOrders = ordersRepository
                .findBySessionUuidAndStatusIn(uuid, List.of("PAID", "COOKING", "READY"));

        List<Map<String, String>> result = activeOrders.stream().map(order -> {
            List<OrderItem> items = orderItemRepository.findByOrder(order);
            String desc = buildDesc(items, order.getOrderNo());

            return Map.of(
                    "orderNo", order.getOrderNo(),
                    "desc",    desc,
                    "status",  order.getStatus()
            );
        }).toList();

        log.info("[ActiveOrder] uuid={}, 진행중 주문 {}건", uuid, result.size());
        return result;
    }

    /**
     * 조리완료 후 주문 상태 COOKING → READY 저장
     * CookingDoneConsumer에서 호출
     */
    @Transactional
    public void markOrderReady(String orderNo) {
        ordersRepository.findByOrderNo(orderNo).ifPresentOrElse(order -> {
            order.ready();
            ordersRepository.save(order);
            log.info("[Order] 주문 상태 READY 저장 완료: orderNo={}", order.getOrderNo());
        }, () -> log.warn("[Order] 주문 없음 — DB 저장 실패: orderNo={}", orderNo));
    }

    private String buildDesc(List<OrderItem> items, String fallback) {
        if (items.isEmpty()) return fallback;
        if (items.size() == 1) return items.get(0).getProductName();
        return items.get(0).getProductName() + " 외 " + (items.size() - 1) + "건";
    }
}

package com.example.orderService.admin;

import com.example.orderService.order.entity.OrderItem;
import com.example.orderService.order.entity.Orders;
import com.example.orderService.order.repository.OrderItemRepository;
import com.example.orderService.order.repository.OrdersRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;


/**
 * 관리자 화면 초기 스냅샷
 * GET /api/admin/sessions/snapshot
 *
 * admin 접속 시 최초 1회 호출.
 * 현재 진행중인 주문(PAID, COOKING) 전체를 반환.
 * 이후 실시간 업데이트는 SSE로 수신.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/sessions")
public class AdminSessionSnapshotController {

    private final OrdersRepository ordersRepository;
    private final OrderItemRepository orderItemRepository;

    @GetMapping("/snapshot")
    public ResponseEntity<List<Map<String, String>>> snapshot() {
        // PAID: 결제완료 후 아직 제조지시 전 (거의 없지만 포함)
        // COOKING: 제조지시 완료, 조리 진행중 — DB status가 PAID인 채로 조리 중일 수 있음
        // → PAID + COOKING 둘 다 포함 (inventory-service가 stock을 COOKING으로 별도 관리)
        List<Orders> activeOrders = ordersRepository
                .findByStatusIn(List.of("PAID", "COOKING", "READY"));

        List<Map<String, String>> result = activeOrders.stream().map(order -> {
            List<OrderItem> items = orderItemRepository.findByOrder(order);
            String desc = items.isEmpty() ? order.getOrderNo()
                    : items.size() == 1
                    ? items.get(0).getProductName()
                    : items.get(0).getProductName() + " 외 " + (items.size() - 1) + "건";

            return Map.of(
                    "uuid",    order.getSessionUuid(),
                    "orderNo", order.getOrderNo(),
                    "desc",    desc,
                    "status",  order.getStatus()
            );
        }).toList();

        log.info("[AdminSnapshot] 진행중 주문 {}건 반환", result.size());
        return ResponseEntity.ok(result);
    }
}

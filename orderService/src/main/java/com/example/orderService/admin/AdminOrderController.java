package com.example.orderService.admin;

import com.example.orderService.order.entity.Orders;
import com.example.orderService.order.repository.OrdersRepository;
import com.example.orderService.common.sse.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 관리자 주문 처리 컨트롤러
 *
 * POST /api/admin/order/receive?orderNo=...
 *   1. 주문 상태 DELIVERED 업데이트
 *   2. 고객 SSE에 ORDER_RECEIVED 이벤트 전송
 *      → 클라이언트가 10초 후 스스로 row 제거 + SSE 종료 요청
 *   3. 관리자 전체에게 fanout 브로드캐스트
 *
 * ※ 서버가 즉시 close() 하면 이벤트가 클라이언트에 도달하기 전 연결이
 *    끊겨 수령 알림이 유실됩니다. SSE 종료는 클라이언트(order.html)가
 *    ORDER_RECEIVED 수신 후 10초 뒤 /sse/close 호출로 처리합니다.
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/order")
public class AdminOrderController {

    private final OrdersRepository ordersRepository;
    private final SseEmitterManager sseEmitterManager;
    private final AdminNotifyPublisher adminNotifyPublisher;

    @PostMapping("/receive")
    public ResponseEntity<Void> receive(@RequestParam String orderNo) {
        Orders order = ordersRepository.findByOrderNo(orderNo)
                .orElseThrow(() -> new IllegalArgumentException("주문 없음: " + orderNo));

        String uuid = order.getSessionUuid();

        // DB 상태 DELIVERED로 업데이트
        order.deliver();
        ordersRepository.save(order);
        log.info("[Admin] 수령 완료: orderNo={}, uuid={}", orderNo, uuid);

        // ① 고객 화면에 ORDER_RECEIVED 이벤트 전송
        //    → 클라이언트가 10초 후 row 제거 후 /sse/close 호출
        sseEmitterManager.send(uuid, "ORDER_RECEIVED", Map.of("orderNo", orderNo));

        // ② 관리자 전체에게 수령 완료 브로드캐스트
        adminNotifyPublisher.orderDelivered(uuid, orderNo);

        return ResponseEntity.ok().build();
    }
}
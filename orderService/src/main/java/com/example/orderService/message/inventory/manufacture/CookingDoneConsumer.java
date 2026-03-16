package com.example.orderService.message.inventory.manufacture;

import com.example.orderService.admin.AdminNotifyPublisher;
import com.example.orderService.config.RabbitConfig;
import com.example.orderService.message.inventory.manufacture.dto.CookingDoneMessage;
import com.example.orderService.order.service.OrderService;
import com.example.orderService.common.sse.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 조리완료 메시지 수신 (order-service) — 메시지 바인딩 전담
 * inventory-service → RabbitMQ → order-service → SSE → 고객 + admin 브로드캐스트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CookingDoneConsumer {

    private final OrderService orderService;
    private final SseEmitterManager sseEmitterManager;
    private final AdminNotifyPublisher adminNotifyPublisher;

    @RabbitListener(queues = RabbitConfig.QUEUE_COOKING_DONE)
    public void consume(CookingDoneMessage message) {
        log.info("[MQ] 조리완료 수신: orderNo={}, sessionUuid={}", message.orderNo(), message.sessionUuid());

        // 주문 DB status COOKING → READY (@Transactional은 OrderService에 위임)
        orderService.markOrderReady(message.orderNo());

        // 고객 SSE에 조리완료 알림
        sseEmitterManager.send(
                message.sessionUuid(),
                "COOKING_DONE",
                Map.of("orderNo", message.orderNo(), "status", "READY")
        );

        // 관리자 전체에게 조리완료 알림
        adminNotifyPublisher.orderReady(message.sessionUuid(), message.orderNo());
    }
}
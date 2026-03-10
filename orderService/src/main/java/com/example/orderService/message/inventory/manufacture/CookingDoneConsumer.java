package com.example.orderService.message.inventory.manufacture;

import com.example.orderService.admin.AdminNotifyPublisher;
import com.example.orderService.config.RabbitConfig;
import com.example.orderService.message.inventory.manufacture.dto.CookingDoneMessage;
import com.example.orderService.order.repository.OrdersRepository;
import com.example.orderService.common.sse.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import org.springframework.transaction.annotation.Transactional;

import java.util.Map;


/**
 * 조리완료 메시지 수신 (order-service)
 * inventory-service → RabbitMQ → order-service → SSE → 고객 + admin 브로드캐스트
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CookingDoneConsumer {

    private final SseEmitterManager sseEmitterManager;
    private final AdminNotifyPublisher adminNotifyPublisher;
    private final OrdersRepository ordersRepository;

    @Transactional
    @RabbitListener(queues = RabbitConfig.QUEUE_COOKING_DONE)
    public void consume(CookingDoneMessage message) {
        log.info("[MQ] 조리완료 수신: orderNo={}, sessionUuid={}", message.orderNo(), message.sessionUuid());

        // 주문 DB status COOKING → READY
        ordersRepository.findByOrderNo(message.orderNo()).ifPresentOrElse(order -> {
            order.ready();
            ordersRepository.save(order);
            log.info("[MQ] 주문 상태 READY 저장 완료: orderNo={}, status={}", order.getOrderNo(), order.getStatus());
        }, () -> {
            log.warn("[MQ] 주문 없음 — DB 저장 실패: orderNo={}", message.orderNo());
        });

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
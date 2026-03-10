package com.example.orderService.admin;

import com.example.orderService.config.RabbitConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 관리자 브로드캐스트 발행
 * admin.notify.exchange (Fanout) → 모든 Task의 AdminNotifyConsumer 수신
 *
 * 이벤트 종류:
 *   SESSION_OPENED   — 고객 SSE 연결
 *   SESSION_CLOSED   — 고객 SSE 종료
 *   ORDER_COOKING    — 결제완료 → 제조 지시
 *   ORDER_READY      — 조리완료
 *   ORDER_DELIVERED  — 수령완료
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminNotifyPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void sessionOpened(String uuid) {
        publish("SESSION_OPENED", Map.of("uuid", uuid));
    }

    public void sessionClosed(String uuid) {
        publish("SESSION_CLOSED", Map.of("uuid", uuid));
    }

    public void orderCooking(String uuid, String orderNo, String desc, String amount) {
        publish("ORDER_COOKING", Map.of(
                "uuid",    uuid,
                "orderNo", orderNo,
                "desc",    desc,
                "amount",  amount
        ));
    }

    public void orderReady(String uuid, String orderNo) {
        publish("ORDER_READY", Map.of(
                "uuid",    uuid,
                "orderNo", orderNo
        ));
    }

    public void orderDelivered(String uuid, String orderNo) {
        publish("ORDER_DELIVERED", Map.of(
                "uuid",    uuid,
                "orderNo", orderNo
        ));
    }

    private void publish(String eventType, Map<String, String> payload) {
        Map<String, Object> message = Map.of(
                "eventType", eventType,
                "payload", payload
        );
        rabbitTemplate.convertAndSend(RabbitConfig.ADMIN_NOTIFY_EXCHANGE, "", message);
        log.debug("[AdminNotify] 발행: event={}, payload={}", eventType, payload);
    }
}
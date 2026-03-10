package com.example.orderService.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 관리자 브로드캐스트 수신
 * admin.notify.exchange (Fanout) → 이 Task의 exclusive queue 수신
 * → AdminSseManager를 통해 이 Task에 연결된 모든 admin에게 SSE 전송
 *
 * 수평확장 시: Task마다 이 Consumer가 독립적으로 동작
 * Task A의 Consumer → Task A에 연결된 admin들에게 전송
 * Task B의 Consumer → Task B에 연결된 admin들에게 전송
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminNotifyConsumer {

    private final AdminSseManager adminSseManager;

    @RabbitListener(queues = "#{adminNotifyQueue.name}")
    public void consume(Map<String, Object> message) {
        String eventType = (String) message.get("eventType");
        @SuppressWarnings("unchecked")
        Map<String, String> payload = (Map<String, String>) message.get("payload");

        log.debug("[AdminNotify] 수신: event={}", eventType);
        adminSseManager.broadcast(eventType, payload);
    }
}
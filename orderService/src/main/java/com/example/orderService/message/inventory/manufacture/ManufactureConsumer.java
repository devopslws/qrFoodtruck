package com.example.orderService.message.inventory.manufacture;

import com.example.orderService.config.RabbitConfig;
import com.example.orderService.message.inventory.manufacture.dto.CookingDoneMessage;
import com.example.orderService.common.sse.SseEmitterManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 조리완료 메시지 수신 (order-service)
 * inventory-service → RabbitMQ → order-service → SSE → 고객
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManufactureConsumer {

    private final SseEmitterManager sseEmitterManager;

    @RabbitListener(queues = RabbitConfig.QUEUE_COOKING_DONE)
    public void consume(CookingDoneMessage message) {
        log.info("[MQ] 조리완료 수신: orderNo={}, sessionUuid={}", message.orderNo(), message.sessionUuid());

        // SSE로 고객에게 조리완료 알림 전송
        sseEmitterManager.send(
                message.sessionUuid(),
                "COOKING_DONE",
                Map.of("orderNo", message.orderNo(), "status", "READY")
        );
    }
}







































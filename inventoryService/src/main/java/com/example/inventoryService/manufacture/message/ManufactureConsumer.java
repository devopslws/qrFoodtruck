package com.example.inventoryService.manufacture.message;

import com.example.inventoryService.config.RabbitConfig;
import com.example.inventoryService.manufacture.dto.ManufactureMessage;
import com.example.inventoryService.manufacture.service.ManufactureService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 제조 지시 수신 (inventory-service)
 * HTTP 바인딩에 해당하는 MQ 수신만 담당 — 비즈니스 로직은 ManufactureService에 위임
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManufactureConsumer {

    private final ManufactureService manufactureService;

    @RabbitListener(queues = RabbitConfig.QUEUE_MANUFACTURE)
    public void consume(ManufactureMessage message) {
        log.info("[MQ] 제조 지시 수신: orderNo={}", message.orderNo());
        manufactureService.processManufacture(message);
    }
}

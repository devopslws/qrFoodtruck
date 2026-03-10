package com.example.orderService.message.inventory.manufacture;

import com.example.orderService.config.RabbitConfig;
import com.example.orderService.message.inventory.manufacture.dto.ManufactureMessage;
import com.example.orderService.order.entity.OrderItem;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 제조 지시 발행 (order-service → inventory-service)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManufacturePublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publish(String orderNo, String sessionUuid, List<OrderItem> items) {
        List<ManufactureMessage.Item> messageItems = items.stream()
                .map(i -> new ManufactureMessage.Item(
                        i.getProductId(),
                        i.getProductName(),
                        i.getQuantity()))
                .toList();

        ManufactureMessage message = new ManufactureMessage(orderNo, sessionUuid, messageItems);

        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_MANUFACTURE,
                message
        );

        log.info("[MQ] 제조 지시 발행: orderNo={}, items={}", orderNo, messageItems.size());
    }
}
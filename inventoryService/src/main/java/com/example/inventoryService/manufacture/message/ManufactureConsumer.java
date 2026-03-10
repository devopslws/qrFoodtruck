package com.example.inventoryService.manufacture.message;

import com.example.inventoryService.config.RabbitConfig;
import com.example.inventoryService.manufacture.dto.CookingDoneMessage;
import com.example.inventoryService.manufacture.dto.ManufactureMessage;
import com.example.inventoryService.manufacture.entity.Manufacture;
import com.example.inventoryService.manufacture.repository.ManufactureRepository;
import com.example.inventoryService.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 제조 지시 수신 (inventory-service)
 *
 * 1. manufacture.queue 메시지 수신
 * 2. MySQL stock_quantity 차감 동기화
 * 3. Manufacture 레코드 생성 (COOKING)
 * 4. 7초 후 조리완료 → DONE 업데이트
 * 5. cooking.done.queue로 조리완료 메시지 발행
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ManufactureConsumer {

    private final ManufactureRepository manufactureRepository;
    private final StockRepository stockRepository;
    private final RabbitTemplate rabbitTemplate;

    @RabbitListener(queues = RabbitConfig.QUEUE_MANUFACTURE)
    public void consume(ManufactureMessage message) {
        log.info("[MQ] 제조 지시 수신: orderNo={}", message.orderNo());

        // MySQL 재고 차감 동기화
        for (ManufactureMessage.Item item : message.items()) {
            stockRepository.findById(item.productId()).ifPresent(stock -> {
                stock.decrease(item.quantity());
                stockRepository.save(stock);
                log.info("[Stock] DB 재고 차감: productId={}, quantity={}", item.productId(), item.quantity());
            });
        }

        // 제조 레코드 생성
        Manufacture manufacture = Manufacture.builder()
                .orderNo(message.orderNo())
                .sessionUuid(message.sessionUuid())
                .status("COOKING")
                .build();
        manufactureRepository.save(manufacture);

        // 7초 후 조리완료 처리
        cookAsync(manufacture, message.sessionUuid());
    }

    @Async
    protected void cookAsync(Manufacture manufacture, String sessionUuid) {
        try {
            Thread.sleep(7_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        manufacture.done();
        manufactureRepository.save(manufacture);
        log.info("[Manufacture] 조리완료: orderNo={}", manufacture.getOrderNo());

        // 조리완료 메시지 발행 → order-service
        rabbitTemplate.convertAndSend(
                RabbitConfig.EXCHANGE,
                RabbitConfig.ROUTING_COOKING_DONE,
                new CookingDoneMessage(manufacture.getOrderNo(), sessionUuid)
        );
    }
}
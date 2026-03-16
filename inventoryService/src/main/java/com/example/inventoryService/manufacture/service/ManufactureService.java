package com.example.inventoryService.manufacture.service;

import com.example.inventoryService.config.RabbitConfig;
import com.example.inventoryService.manufacture.dto.CookingDoneMessage;
import com.example.inventoryService.manufacture.dto.ManufactureMessage;
import com.example.inventoryService.manufacture.entity.Manufacture;
import com.example.inventoryService.manufacture.repository.ManufactureRepository;
import com.example.inventoryService.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

/**
 * 제조 비즈니스 로직 (inventory-service)
 *
 * ※ @Async 버그 수정
 *   ManufactureConsumer에서 this.cookAsync()를 직접 호출하면
 *   Spring AOP 프록시를 우회하여 실제로 동기 실행된다.
 *   ManufactureService를 별도 빈으로 분리함으로써
 *   Spring이 프록시를 통해 cookAsync()를 호출하여 정상 비동기 동작한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ManufactureService {

    private final ManufactureRepository manufactureRepository;
    private final StockRepository stockRepository;
    private final RabbitTemplate rabbitTemplate;

    /**
     * 제조 지시 처리
     *  1. MySQL stock_quantity 차감 동기화
     *  2. Manufacture(COOKING) 레코드 생성
     *  3. cookAsync() 호출 — Spring 프록시를 통해 실제 비동기 실행됨
     */
    public void processManufacture(ManufactureMessage message) {
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
        log.info("[Manufacture] 제조 시작: orderNo={}", manufacture.getOrderNo());

        // 비동기 조리 시작 — 별도 빈(ManufactureService)을 통한 호출이므로 AOP 프록시 적용됨
        cookAsync(manufacture, message.sessionUuid());
    }

    /**
     * 비동기 조리 완료 처리
     *  7초 대기 → Manufacture(DONE) 저장 → cooking.done.queue 발행
     */
    @Async
    public void cookAsync(Manufacture manufacture, String sessionUuid) {
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

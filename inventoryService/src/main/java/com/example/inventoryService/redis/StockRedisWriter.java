package com.example.inventoryService.redis;

import com.example.inventoryService.stock.entity.Stock;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

import static com.example.inventoryService.redis.RedisKeyConstants.*;

/**
 * Redis Write 전담 컴포넌트 (inventory-service 전용)
 *
 * Server B만 Redis에 Write한다.
 * Server A(order-service)는 StockRedisReader 인터페이스를 통해 Read만 수행.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockRedisWriter {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 서버 시작 시 DB의 전체 상품을 Redis에 적재 (ApplicationRunner에서 호출)
     * - product:{id} Hash: 메뉴 조회용
     * - stock:{id}   String: 재고 차감용
     * - displayed:{id} String: 전시 여부 검증용
     */
    public void loadAll(List<Stock> products) {
        for (Stock product : products) {
            loadOne(product);
        }
        log.info("[Redis] 전체 상품 {}개 적재 완료", products.size());
    }

    public void loadOne(Stock product) {
        Long id = product.getId();

        // Hash: 고객 메뉴 조회용
        redisTemplate.opsForHash().putAll(productKey(id), Map.of(
                FIELD_NAME,      product.getName(),
                FIELD_PRICE,     String.valueOf(product.getPrice()),
                FIELD_QUANTITY,  String.valueOf(product.getQuantity()),
                FIELD_DISPLAYED, String.valueOf(product.isDisplayed())
        ));

        // String: 주문 시 재고 검증·차감용 (원자적 DECR을 위해 분리)
        redisTemplate.opsForValue().set(stockKey(id), String.valueOf(product.getQuantity()));

        // String: 주문 시 전시 여부 검증용
        redisTemplate.opsForValue().set(displayedKey(id), String.valueOf(product.isDisplayed()));

        log.debug("[Redis] 상품 적재: id={}, name={}, stock={}", id, product.getName(), product.getQuantity());
    }

    /**
     * 재고 차감 — 주문 수락 시 호출
     * DECR은 원자적이므로 동시 요청에도 정확하게 차감됨
     *
     * @return 차감 후 남은 재고 (음수면 재고 부족 — 호출부에서 검증 필요)
     */
    public long decrease(Long productId, int quantity) {
        long remaining = 0;
        for (int i = 0; i < quantity; i++) {
            remaining = redisTemplate.opsForValue().decrement(stockKey(productId));
        }

        // Hash의 quantity 필드도 동기화
        redisTemplate.opsForHash().put(productKey(productId), FIELD_QUANTITY, String.valueOf(remaining));

        log.info("[Redis] 재고 차감: productId={}, quantity={}, remaining={}", productId, quantity, remaining);
        return remaining;
    }

    /**
     * 재고 복구 — 주문 취소·환불 시 호출
     */
    public long restore(Long productId, int quantity) {
        long restored = redisTemplate.opsForValue().increment(stockKey(productId), quantity);

        // Hash 동기화
        redisTemplate.opsForHash().put(productKey(productId), FIELD_QUANTITY, String.valueOf(restored));

        log.info("[Redis] 재고 복구: productId={}, quantity={}, total={}", productId, quantity, restored);
        return restored;
    }

    /**
     * 전시 여부 변경 — 관리자가 메뉴 on/off 시 호출
     */
    public void setDisplayed(Long productId, boolean displayed) {
        redisTemplate.opsForValue().set(displayedKey(productId), String.valueOf(displayed));
        redisTemplate.opsForHash().put(productKey(productId), FIELD_DISPLAYED, String.valueOf(displayed));

        log.info("[Redis] 전시 여부 변경: productId={}, displayed={}", productId, displayed);
    }

    /**
     * DB 재고와 Redis 재고 강제 동기화 — 관리자 수동 재고 조정 시 호출
     */
    public void syncStock(Long productId, int quantity) {
        redisTemplate.opsForValue().set(stockKey(productId), String.valueOf(quantity));
        redisTemplate.opsForHash().put(productKey(productId), FIELD_QUANTITY, String.valueOf(quantity));

        log.info("[Redis] 재고 강제 동기화: productId={}, quantity={}", productId, quantity);
    }
}
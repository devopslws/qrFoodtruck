package com.example.inventoryService.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

import static com.example.inventoryService.redis.RedisKeyConstants.*;


/**
 * Redis Read 전담 컴포넌트 (inventory-service 내부용)
 *
 * order-service는 별도의 동일한 인터페이스로 읽기만 수행.
 * inventory-service 내부에서도 주문 검증 시 이 클래스를 사용.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockRedisReader {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 재고 수량 조회
     * Redis에 값이 없으면 -1 반환 (캐시 미스 — 호출부에서 DB fallback 처리)
     */
    public int getStock(Long productId) {
        String value = redisTemplate.opsForValue().get(stockKey(productId));
        if (value == null) {
            log.warn("[Redis] 재고 캐시 미스: productId={}", productId);
            return -1;
        }
        return Integer.parseInt(value);
    }

    /**
     * 전시 여부 조회
     */
    public boolean isDisplayed(Long productId) {
        String value = redisTemplate.opsForValue().get(displayedKey(productId));
        if (value == null) {
            log.warn("[Redis] 전시여부 캐시 미스: productId={}", productId);
            return false; // 안전한 기본값 — 전시 안 함으로 처리
        }
        return Boolean.parseBoolean(value);
    }

    /**
     * 주문 가능 여부 통합 검증
     * - 전시 중인가?
     * - 재고가 요청 수량 이상인가?
     */
    public boolean isOrderable(Long productId, int requestedQuantity) {
        if (!isDisplayed(productId)) {
            log.info("[Redis] 주문 불가 - 전시 off: productId={}", productId);
            return false;
        }
        int stock = getStock(productId);
        if (stock < requestedQuantity) {
            log.info("[Redis] 주문 불가 - 재고 부족: productId={}, stock={}, requested={}", productId, stock, requestedQuantity);
            return false;
        }
        return true;
    }

    /**
     * 메뉴 조회용 Hash 전체 반환
     * order-service에서 고객 메뉴 화면 렌더링 시 사용
     */
    public Map<Object, Object> getProductInfo(Long productId) {
        return redisTemplate.opsForHash().entries(productKey(productId));
    }
}
package com.example.orderService.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static com.example.orderService.redis.RedisKeyConstants.*;

/**
 * order-service Redis Read 전담 컴포넌트
 *
 * inventory-service가 Write한 재고 데이터를 읽기만 함.
 * Write 메서드 없음 — 구조적으로 Read Only 강제.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockRedisReader {

    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 재고 조회
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
        if (value == null) return false;
        return Boolean.parseBoolean(value);
    }

    /**
     * 단일 상품 Hash 조회 — 메뉴 상세
     */
    public Map<Object, Object> getProductInfo(Long productId) {
        return redisTemplate.opsForHash().entries(productKey(productId));
    }

    /**
     * 전체 상품 Hash 조회 — 고객 메뉴 화면 최초 진입 시
     * product:1, product:2 ... 순차 조회
     * (상품 수가 적으므로 SCAN 불필요)
     */
    public List<Map<Object, Object>> getAllProducts(List<Long> productIds) {
        List<Map<Object, Object>> result = new ArrayList<>();
        for (Long id : productIds) {
            Map<Object, Object> info = redisTemplate.opsForHash().entries(productKey(id));
            if (!info.isEmpty()) {
                info.put("id", String.valueOf(id)); // id 필드 추가
                result.add(info);
            }
        }
        return result;
    }

    /**
     * 주문 가능 여부 사전 검증 (주문 요청 직후 빠른 체크)
     * 최종 차감은 inventory-service(Server B)가 담당
     */
    public boolean isOrderable(Long productId, int requestedQuantity) {
        if (!isDisplayed(productId)) return false;
        return getStock(productId) >= requestedQuantity;
    }
}
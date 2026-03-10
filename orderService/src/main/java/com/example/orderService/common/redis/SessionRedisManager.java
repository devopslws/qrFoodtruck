package com.example.orderService.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.UUID;

import static com.example.orderService.common.redis.RedisKeyConstants.*;

/**
 * 고객 익명 세션 관리
 *
 * [세션 생명주기]
 * 1. QR 접속 → UUID 발급 → session:{uuid} 생성
 * 2. 첫 상품 담기 → SSE 연결 시작
 * 3. 장바구니 비어있고 진행중 주문 없으면 → SSE 종료 → 세션 만료
 * 4. 비정상 이탈 → Heartbeat 실패 감지 → 세션 만료
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRedisManager {

    private final RedisTemplate<String, String> redisTemplate;

    // 진행중 주문 수 키: active-orders:{uuid} → count
    private static final String ACTIVE_ORDERS_KEY = "active-orders:";

    /**
     * 새 익명 세션 발급
     */
    public String createSession() {
        String uuid = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(sessionKey(uuid), "ACTIVE");
        log.info("[Session] 신규 세션 발급: {}", uuid);
        return uuid;
    }

    /**
     * 세션 존재 여부 확인
     */
    public boolean exists(String uuid) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(uuid)));
    }

    /**
     * 세션 TTL 갱신
     */
    public void refresh(String uuid) {
        redisTemplate.persist(sessionKey(uuid)); // TTL 제거 (SSE가 생명주기 관리)
    }

    /**
     * 세션 즉시 만료
     */
    public void expire(String uuid) {
        redisTemplate.delete(sessionKey(uuid));
        redisTemplate.delete(cartKey(uuid));
        redisTemplate.delete(ACTIVE_ORDERS_KEY + uuid);
        log.info("[Session] 세션 만료: {}", uuid);
    }

    // ── 장바구니 ────────────────────────────────────────────

    public void putCart(String uuid, Long productId, int quantity) {
        if (quantity <= 0) {
            redisTemplate.opsForHash().delete(cartKey(uuid), String.valueOf(productId));
        } else {
            redisTemplate.opsForHash().put(cartKey(uuid), String.valueOf(productId), String.valueOf(quantity));
        }
    }

    public Map<Object, Object> getCart(String uuid) {
        return redisTemplate.opsForHash().entries(cartKey(uuid));
    }

    public void clearCart(String uuid) {
        redisTemplate.delete(cartKey(uuid));
        log.info("[Session] 장바구니 초기화: {}", uuid);
    }

    /**
     * 장바구니 비어있는지 확인
     */
    public boolean isCartEmpty(String uuid) {
        Long size = redisTemplate.opsForHash().size(cartKey(uuid));
        return size == null || size == 0;
    }

    // ── 진행중 주문 관리 ────────────────────────────────────

    /**
     * 주문 생성 시 진행중 주문 수 증가
     */
    public void incrementActiveOrders(String uuid) {
        redisTemplate.opsForValue().increment(ACTIVE_ORDERS_KEY + uuid);
        log.debug("[Session] 진행중 주문 +1: {}", uuid);
    }

    /**
     * 주문 수령 완료 시 진행중 주문 수 감소
     */
    public void decrementActiveOrders(String uuid) {
        String key = ACTIVE_ORDERS_KEY + uuid;
        Long count = redisTemplate.opsForValue().decrement(key);
        if (count != null && count <= 0) {
            redisTemplate.delete(key);
        }
        log.debug("[Session] 진행중 주문 -1: {}", uuid);
    }

    /**
     * 진행중 주문 없는지 확인
     */
    public boolean hasNoActiveOrders(String uuid) {
        String value = redisTemplate.opsForValue().get(ACTIVE_ORDERS_KEY + uuid);
        if (value == null) return true;
        return Integer.parseInt(value) <= 0;
    }
}
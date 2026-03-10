package com.example.orderService.common.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

/**
 * 고객 익명 세션 관리
 * [세션 생명주기]
 * 1. /orderView 접근 → uuid 발급 → sessionStorage 저장 (쿠키 X)
 * 2. 첫 상품 담기 → SSE 연결
 * 3. idle 타임아웃 or 결제 실패 → SSE 종료 (uuid는 유지)
 * 4. 헬스체크 2회 이상 미응답 → uuid 만료 (스케줄러)
 * 5. Toss 리다이렉트 복귀 → uuid 재사용 → SSE 재연결
 *
 * [Redis 키]
 * session:{uuid}        → "ACTIVE" (TTL: 10분, 헬스체크마다 갱신)
 * last-seen:{uuid}      → epoch millis
 * cart:{uuid}           → Hash (productId → quantity)
 * active-orders:{uuid}  → count
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRedisManager {

    private final RedisTemplate<String, String> redisTemplate;

    private static final Duration SESSION_TTL = Duration.ofMinutes(10);
    private static final String ACTIVE_ORDERS_KEY = "active-orders:";
    private static final String LAST_SEEN_KEY     = "last-seen:";

    // ── 세션 ────────────────────────────────────────────────

    /**
     * uuid 등록 (서버는 발급만, sessionStorage는 클라이언트 관리)
     */
    public void registerUuid(String uuid) {
        redisTemplate.opsForValue().set(sessionKey(uuid), "ACTIVE", SESSION_TTL);
        refreshLastSeen(uuid);
        log.info("[Session] uuid 등록: {}", uuid);
    }

    public boolean exists(String uuid) {
        return Boolean.TRUE.equals(redisTemplate.hasKey(sessionKey(uuid)));
    }

    /**
     * 헬스체크 수신 시 TTL + lastSeen 갱신
     */
    public void refreshLastSeen(String uuid) {
        redisTemplate.opsForValue().set(sessionKey(uuid), "ACTIVE", SESSION_TTL);
        redisTemplate.opsForValue().set(
                LAST_SEEN_KEY + uuid,
                String.valueOf(Instant.now().toEpochMilli()),
                SESSION_TTL
        );
    }

    /**
     * lastSeen 조회 (스케줄러 허수 감지용)
     */
    public long getLastSeen(String uuid) {
        String val = redisTemplate.opsForValue().get(LAST_SEEN_KEY + uuid);
        return val != null ? Long.parseLong(val) : 0L;
    }

    /**
     * 세션 즉시 만료
     */
    public void expire(String uuid) {
        redisTemplate.delete(sessionKey(uuid));
        redisTemplate.delete(cartKey(uuid));
        redisTemplate.delete(ACTIVE_ORDERS_KEY + uuid);
        redisTemplate.delete(LAST_SEEN_KEY + uuid);
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
    }

    public boolean isCartEmpty(String uuid) {
        Long size = redisTemplate.opsForHash().size(cartKey(uuid));
        return size == null || size == 0;
    }

    // ── 진행중 주문 ─────────────────────────────────────────

    public void incrementActiveOrders(String uuid) {
        redisTemplate.opsForValue().increment(ACTIVE_ORDERS_KEY + uuid);
    }

    public void decrementActiveOrders(String uuid) {
        String key = ACTIVE_ORDERS_KEY + uuid;
        Long count = redisTemplate.opsForValue().decrement(key);
        if (count != null && count <= 0) redisTemplate.delete(key);
    }

    public boolean hasNoActiveOrders(String uuid) {
        String val = redisTemplate.opsForValue().get(ACTIVE_ORDERS_KEY + uuid);
        return val == null || Integer.parseInt(val) <= 0;
    }

    // ── key 헬퍼 ────────────────────────────────────────────

    private String sessionKey(String uuid) { return "session:" + uuid; }
    private String cartKey(String uuid)    { return "cart:" + uuid; }
}
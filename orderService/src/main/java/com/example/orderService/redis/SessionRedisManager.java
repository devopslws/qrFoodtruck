package com.example.orderService.redis;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

import static com.example.orderService.redis.RedisKeyConstants.*;

/**
 * 고객 익명 세션 관리
 *
 * [세션 생명주기]
 * 1. QR 접속 → UUID 발급 → session:{uuid} 생성 (30분 TTL)
 * 2. 장바구니 첫 담기 → SSE 연결 + idle 타이머 시작
 * 3. 30초 무활동 → 장바구니 초기화 + 세션 TTL 리셋
 * 4. 모든 주문 수령 완료 → 세션 즉시 만료
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SessionRedisManager {

    private final RedisTemplate<String, String> redisTemplate;

    private static final Duration SESSION_TTL = Duration.ofMinutes(30);
    private static final Duration CART_IDLE_TTL = Duration.ofSeconds(30);

    /**
     * 새 익명 세션 발급
     * 고객이 QR로 최초 접속할 때 호출
     */
    public String createSession() {
        String uuid = UUID.randomUUID().toString();
        redisTemplate.opsForValue().set(sessionKey(uuid), "ACTIVE", SESSION_TTL);
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
     * 세션 TTL 연장 (활동 감지 시)
     */
    public void refresh(String uuid) {
        redisTemplate.expire(sessionKey(uuid), SESSION_TTL);
    }

    /**
     * 세션 즉시 만료 (수령 완료 후)
     */
    public void expire(String uuid) {
        redisTemplate.delete(sessionKey(uuid));
        redisTemplate.delete(cartKey(uuid));
        log.info("[Session] 세션 만료: {}", uuid);
    }

    // ── 장바구니 ────────────────────────────────

    /**
     * 장바구니에 상품 추가/수량 변경
     * cart:{uuid} Hash: {productId: quantity}
     */
    public void putCart(String uuid, Long productId, int quantity) {
        if (quantity <= 0) {
            redisTemplate.opsForHash().delete(cartKey(uuid), String.valueOf(productId));
        } else {
            redisTemplate.opsForHash().put(cartKey(uuid), String.valueOf(productId), String.valueOf(quantity));
        }
        // 장바구니 활동 시 idle 타이머 리셋
        redisTemplate.expire(cartKey(uuid), CART_IDLE_TTL);
    }

    /**
     * 장바구니 전체 조회
     */
    public java.util.Map<Object, Object> getCart(String uuid) {
        return redisTemplate.opsForHash().entries(cartKey(uuid));
    }

    /**
     * 장바구니 초기화 (30초 무활동 시)
     */
    public void clearCart(String uuid) {
        redisTemplate.delete(cartKey(uuid));
        log.info("[Session] 장바구니 초기화: {}", uuid);
    }
}
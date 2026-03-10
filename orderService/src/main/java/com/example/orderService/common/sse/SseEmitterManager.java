package com.example.orderService.common.sse;

import com.example.orderService.common.redis.SessionRedisManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * SSE 연결 관리
 *
 * [생명주기]
 * 시작: 첫 상품 장바구니 담기 → POST /sse/connect
 * 유지: 장바구니에 상품 있거나 진행중 주문 있는 경우
 * 종료: 장바구니 비어있고 진행중 주문 없음 + 1분 타임아웃
 *
 * [Heartbeat]
 * 30초마다 ping 전송 → 실패 시 연결 끊김으로 판단 → 세션 만료
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterManager {

    private final SessionRedisManager sessionRedisManager;

    // uuid → SseEmitter
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    // uuid → Heartbeat 스케줄러
    private final Map<String, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();

    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    private static final long SSE_TIMEOUT_MS = 60_000L;       // 1분 타임아웃
    private static final long HEARTBEAT_INTERVAL_SEC = 30L;   // 30초 heartbeat

    /**
     * SSE 연결 생성
     * 첫 상품 장바구니 담기 시 호출
     */
    public SseEmitter connect(String uuid) {
        // 기존 연결 있으면 재사용
        if (emitters.containsKey(uuid)) {
            log.info("[SSE] 기존 연결 재사용: {}", uuid);
            return emitters.get(uuid);
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 정상 종료
        emitter.onCompletion(() -> {
            log.info("[SSE] 연결 종료: {}", uuid);
            cleanup(uuid);
        });

        // 타임아웃 (1분)
        emitter.onTimeout(() -> {
            log.info("[SSE] 타임아웃: {}", uuid);
            emitter.complete();
            cleanup(uuid);
        });

        // 에러 (비정상 종료)
        emitter.onError(e -> {
            log.warn("[SSE] 연결 에러: uuid={}, error={}", uuid, e.getMessage());
            cleanup(uuid);
        });

        emitters.put(uuid, emitter);

        // Heartbeat 시작
        startHeartbeat(uuid, emitter);

        // 연결 확인용 초기 이벤트 전송
        send(uuid, "CONNECTED", "SSE 연결 완료");

        log.info("[SSE] 신규 연결: {}", uuid);
        return emitter;
    }

    /**
     * 클라이언트에 이벤트 전송
     */
    public void send(String uuid, String eventName, Object data) {
        SseEmitter emitter = emitters.get(uuid);
        if (emitter == null) return;

        try {
            emitter.send(SseEmitter.event()
                    .name(eventName)
                    .data(data));
        } catch (Exception e) {
            log.warn("[SSE] 전송 실패: uuid={}, event={}", uuid, eventName);
            cleanup(uuid);
        }
    }

    /**
     * 종료 조건 체크 후 충족 시 SSE 종료
     * - 장바구니 비어있고
     * - 진행중 주문 없으면
     */
    public void checkAndClose(String uuid) {
        boolean cartEmpty = sessionRedisManager.isCartEmpty(uuid);
        boolean noActiveOrders = sessionRedisManager.hasNoActiveOrders(uuid);

        if (cartEmpty && noActiveOrders) {
            log.info("[SSE] 종료 조건 충족: {}", uuid);
            close(uuid);
        }
    }

    /**
     * SSE 강제 종료 (수동 호출용)
     */
    public void close(String uuid) {
        SseEmitter emitter = emitters.get(uuid);
        if (emitter != null) {
            send(uuid, "SESSION_END", "세션 종료");
            emitter.complete();
        }
        cleanup(uuid);
        sessionRedisManager.expire(uuid);
    }

    /**
     * 연결된 uuid 목록 반환 (관리자 화면용)
     */
    public int getActiveCount() {
        return emitters.size();
    }

    // ── private ──────────────────────────────────────────────

    private void startHeartbeat(String uuid, SseEmitter emitter) {
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                emitter.send(SseEmitter.event().comment("ping"));
                log.debug("[SSE] Heartbeat: {}", uuid);
            } catch (Exception e) {
                log.warn("[SSE] Heartbeat 실패 - 연결 끊김: {}", uuid);
                cleanup(uuid);
            }
        }, HEARTBEAT_INTERVAL_SEC, HEARTBEAT_INTERVAL_SEC, TimeUnit.SECONDS);

        heartbeats.put(uuid, future);
    }

    private void cleanup(String uuid) {
        emitters.remove(uuid);

        ScheduledFuture<?> future = heartbeats.remove(uuid);
        if (future != null) {
            future.cancel(true);
        }
    }
}
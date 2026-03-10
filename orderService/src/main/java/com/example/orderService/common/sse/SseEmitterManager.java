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
 * 고객 SSE 연결 관리
 *
 * [생명주기]
 *   connect()  : 첫 상품 담기 or Toss 리다이렉트 복귀 시
 *   send()     : COOKING_DONE / ORDER_RECEIVED 등 이벤트 전송
 *   close()    : 클라이언트가 /sse/close 호출 시 (정상 종료)
 *   cleanup()  : onCompletion / onTimeout / Heartbeat 실패 시 (비정상 종료)
 *
 * [재연결 전략]
 *   Toss 리다이렉트로 페이지 재로드 시 동일 uuid로 재연결.
 *   기존 emitter를 Map에서 제거만 하고 complete()는 호출하지 않음.
 *   → complete() 호출 시 비동기 콜백이 새 emitter를 잘못 cleanup하는
 *     경쟁 조건(race condition)을 방지하기 위함.
 *
 * [Heartbeat]
 *   30초마다 SSE comment ping 전송.
 *   실패 시 클라이언트가 이미 끊겼다고 판단 → cleanup만 수행.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SseEmitterManager {

    private final SessionRedisManager sessionRedisManager;
    private final com.example.orderService.admin.AdminNotifyPublisher adminNotifyPublisher;

    private final Map<String, SseEmitter>        emitters   = new ConcurrentHashMap<>();
    private final Map<String, ScheduledFuture<?>> heartbeats = new ConcurrentHashMap<>();
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(4);

    private static final long SSE_TIMEOUT_MS       = 60_000L;  // 1분
    private static final long HEARTBEAT_INTERVAL_S = 30L;      // 30초

    // ── 연결 ─────────────────────────────────────────────────

    public SseEmitter connect(String uuid) {
        // 기존 emitter 제거 (complete() 호출 금지 — 콜백 경쟁 조건 방지)
        SseEmitter old = emitters.remove(uuid);
        if (old != null) {
            log.info("[SSE] 재연결 — 기존 emitter 교체: {}", uuid);
            ScheduledFuture<?> oldHb = heartbeats.remove(uuid);
            if (oldHb != null) oldHb.cancel(true);
        }

        SseEmitter emitter = new SseEmitter(SSE_TIMEOUT_MS);

        // 콜백: 자신이 현재 등록된 emitter일 때만 cleanup (교체된 구버전 콜백 무시)
        emitter.onCompletion(() -> {
            if (emitters.get(uuid) == emitter) {
                log.info("[SSE] 연결 종료 (완료): {}", uuid);
                cleanup(uuid);
            }
        });
        emitter.onTimeout(() -> {
            if (emitters.get(uuid) == emitter) {
                log.info("[SSE] 연결 종료 (타임아웃): {}", uuid);
                // emitter.complete() 생략 — AsyncRequestTimeoutException 콘솔 노이즈 방지
                cleanup(uuid);
            }
        });
        emitter.onError(e -> {
            if (emitters.get(uuid) == emitter) {
                // 브라우저 탭 닫기 / 새로고침 등 정상 케이스 → DEBUG
                log.debug("[SSE] 연결 끊김 (클라이언트 종료): uuid={}", uuid);
                cleanup(uuid);
            }
        });

        emitters.put(uuid, emitter);
        startHeartbeat(uuid, emitter);

        send(uuid, "CONNECTED", "SSE 연결 완료");
        adminNotifyPublisher.sessionOpened(uuid);

        log.info("[SSE] 연결 등록: uuid={}, 현재 연결 수={}", uuid, emitters.size());
        return emitter;
    }

    // ── 이벤트 전송 ──────────────────────────────────────────

    public void send(String uuid, String eventName, Object data) {
        SseEmitter emitter = emitters.get(uuid);
        if (emitter == null) {
            // 클라이언트가 이미 끊긴 경우 → 정상 케이스, DEBUG
            log.debug("[SSE] 전송 스킵 (emitter 없음): uuid={}, event={}", uuid, eventName);
            return;
        }
        try {
            emitter.send(SseEmitter.event().name(eventName).data(data));
        } catch (Exception e) {
            // IOException: 클라이언트 연결이 이미 닫힌 경우 → 정상 케이스, DEBUG
            log.debug("[SSE] 전송 실패 (연결 닫힘): uuid={}, event={}", uuid, eventName);
            cleanup(uuid);
        }
    }

    // ── 종료 ─────────────────────────────────────────────────

    /**
     * 서버 주도 정상 종료 (/sse/close or 자동 종료 조건 충족 시)
     * SESSION_END 전송 후 cleanup. 클라이언트가 이미 끊긴 경우 전송 실패는 무시.
     */
    public void close(String uuid) {
        if (!emitters.containsKey(uuid)) {
            sessionRedisManager.expire(uuid);
            adminNotifyPublisher.sessionClosed(uuid);
            return;
        }
        send(uuid, "SESSION_END", "세션 종료");
        cleanup(uuid);
        sessionRedisManager.expire(uuid);
        adminNotifyPublisher.sessionClosed(uuid);
        log.info("[SSE] 세션 종료: {}", uuid);
    }

    public void checkAndClose(String uuid) {
        if (sessionRedisManager.isCartEmpty(uuid)
                && sessionRedisManager.hasNoActiveOrders(uuid)) {
            close(uuid);
        }
    }

    public int getActiveCount() {
        return emitters.size();
    }

    // ── private ──────────────────────────────────────────────

    private void startHeartbeat(String uuid, SseEmitter emitter) {
        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            // heartbeat 대상이 교체된 emitter면 자신을 중단
            if (emitters.get(uuid) != emitter) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                emitter.send(SseEmitter.event().comment("ping"));
                log.debug("[SSE] Heartbeat: {}", uuid);
            } catch (Exception e) {
                log.debug("[SSE] Heartbeat 실패 (연결 끊김): {}", uuid);
                cleanup(uuid);
            }
        }, HEARTBEAT_INTERVAL_S, HEARTBEAT_INTERVAL_S, TimeUnit.SECONDS);

        heartbeats.put(uuid, future);
    }

    private void cleanup(String uuid) {
        emitters.remove(uuid);
        ScheduledFuture<?> future = heartbeats.remove(uuid);
        if (future != null) future.cancel(true);
    }
}
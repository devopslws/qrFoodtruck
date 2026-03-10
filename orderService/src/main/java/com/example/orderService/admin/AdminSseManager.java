package com.example.orderService.admin;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 관리자 SSE 연결 관리
 * - 관리자는 uuid 없이 임의 key로 연결 (브라우저 탭 단위)
 * - 이벤트 발생 시 이 Task에 연결된 모든 admin에게 브로드캐스트
 * - AdminNotifyConsumer가 RabbitMQ 메시지를 수신해서 broadcast() 호출
 */
@Slf4j
@Component
public class AdminSseManager {

    // adminKey → SseEmitter (adminKey = 브라우저 탭별 임의 UUID)
    private final Map<String, SseEmitter> emitters = new ConcurrentHashMap<>();

    private static final long ADMIN_SSE_TIMEOUT_MS = 3_600_000L; // 1시간

    public SseEmitter connect(String adminKey) {
        SseEmitter emitter = new SseEmitter(ADMIN_SSE_TIMEOUT_MS);

        emitter.onCompletion(() -> {
            emitters.remove(adminKey);
            log.info("[AdminSSE] 연결 종료: {}", adminKey);
        });
        emitter.onTimeout(() -> {
            emitters.remove(adminKey);
            log.info("[AdminSSE] 타임아웃: {}", adminKey);
        });
        emitter.onError(e -> {
            emitters.remove(adminKey);
            log.warn("[AdminSSE] 에러: {}", adminKey);
        });

        emitters.put(adminKey, emitter);

        // 초기 연결 확인 이벤트
        try {
            emitter.send(SseEmitter.event().name("ADMIN_CONNECTED").data("connected"));
        } catch (Exception e) {
            emitters.remove(adminKey);
        }

        log.info("[AdminSSE] 신규 연결: adminKey={}, 총 {}명", adminKey, emitters.size());
        return emitter;
    }

    /**
     * 이 Task에 연결된 모든 admin에게 브로드캐스트
     */
    public void broadcast(String eventName, Object data) {
        if (emitters.isEmpty()) return;

        emitters.forEach((key, emitter) -> {
            try {
                emitter.send(SseEmitter.event().name(eventName).data(data));
            } catch (Exception e) {
                log.warn("[AdminSSE] 전송 실패: adminKey={}", key);
                emitters.remove(key);
            }
        });

        log.debug("[AdminSSE] 브로드캐스트: event={}, 대상={}명", eventName, emitters.size());
    }

}
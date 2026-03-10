package com.example.orderService.common.sse;

import com.example.orderService.common.redis.SessionRedisManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 연결 엔드포인트
 *
 * GET  /sse/connect?uuid=  — SSE 연결 (첫 상품 담기 or 복귀 시)
 * POST /sse/close?uuid=    — SSE 명시적 종료
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sse")
public class SseController {

    private final SseEmitterManager sseEmitterManager;
    private final SessionRedisManager sessionRedisManager;

    /**
     * GET /sse/connect?uuid=...
     * 쿠키 없음. uuid는 클라이언트 sessionStorage에서 전달.
     * - 첫 상품 담기 시
     * - Toss 리다이렉트 복귀 후 진행중 주문 있을 때
     */
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestParam String uuid) {
        if (!sessionRedisManager.exists(uuid)) {
            // 세션 만료 후 재연결 시도 (정상 케이스 — 클라이언트가 재연결 로직 실행 중)
            // uuid를 재등록하고 연결 허용 (heartbeat가 계속 살아있으면 TTL 갱신됨)
            log.debug("[SSE] 만료된 uuid 재연결 — uuid 재등록 후 허용: {}", uuid);
            sessionRedisManager.registerUuid(uuid);
        }
        return sseEmitterManager.connect(uuid);
    }

    /**
     * POST /sse/close?uuid=...
     * idle 타임아웃, 결제 실패 등 클라이언트가 명시적으로 종료
     */
    @PostMapping("/close")
    public ResponseEntity<Void> close(@RequestParam String uuid) {
        sseEmitterManager.close(uuid);
        return ResponseEntity.ok().build();
    }
}
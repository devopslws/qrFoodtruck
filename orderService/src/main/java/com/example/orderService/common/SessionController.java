package com.example.orderService.common;

import com.example.orderService.common.redis.SessionRedisManager;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

/**
 * 익명 세션 관리 컨트롤러
 *
 * GET  /api/session/init       — uuid 발급 (페이지 최초 진입 시)
 * POST /api/session/heartbeat  — 생존 신호 수신 (30초마다)
 * GET  /api/session/order/active — 진행중 주문 조회 (복귀 시 화면 복구용)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/session")
public class SessionController {

    private final SessionRedisManager sessionRedisManager;

    /**
     * uuid 발급
     * 페이지 최초 진입 시 호출. 쿠키 사용 안 함.
     * 클라이언트가 sessionStorage에 보관.
     */
    @GetMapping("/init")
    public ResponseEntity<Map<String, String>> init() {
        String uuid = UUID.randomUUID().toString();
        sessionRedisManager.registerUuid(uuid);
        log.info("[Session] uuid 발급: {}", uuid);
        return ResponseEntity.ok(Map.of("uuid", uuid));
    }

    /**
     * 헬스체크 (클라이언트 → 서버)
     * 30초마다 클라이언트가 호출. 서버는 lastSeen 갱신.
     * 2회 연속 미응답 시 uuid 만료 처리는 스케줄러가 담당.
     */
    @PostMapping("/heartbeat")
    public ResponseEntity<Void> heartbeat(@RequestParam String uuid) {
        if (!sessionRedisManager.exists(uuid)) {
            log.warn("[Session] 유효하지 않은 uuid 헬스체크: {}", uuid);
            return ResponseEntity.notFound().build();
        }
        sessionRedisManager.refreshLastSeen(uuid);
        log.debug("[Session] 헬스체크: {}", uuid);
        return ResponseEntity.ok().build();
    }
}
package com.example.orderService.common.sse;

import com.example.orderService.common.redis.SessionRedisManager;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

/**
 * SSE 연결 엔드포인트
 *
 * GET  /sse/connect  → SSE 연결 시작 (첫 상품 담기 시 호출)
 * POST /sse/close    → SSE 연결 종료 (클라이언트 요청 시)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/sse")
public class SseController {

    private static final String SESSION_COOKIE = "SESSION_ID";

    private final SseEmitterManager sseEmitterManager;
    private final SessionRedisManager sessionRedisManager;

    /**
     * GET /sse/connect
     * 첫 상품 장바구니 담기 시 호출
     * 쿠키의 SESSION_ID로 uuid 식별
     */
    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(HttpServletRequest request, HttpServletResponse response) {
        String uuid = resolveUuid(request);

        // 세션 없으면 신규 발급
        if (uuid == null || !sessionRedisManager.exists(uuid)) {
            uuid = sessionRedisManager.createSession();
            Cookie cookie = new Cookie("SESSION_ID", uuid);
            cookie.setHttpOnly(true);
            cookie.setPath("/");
            cookie.setMaxAge(-1);
            response.addCookie(cookie);
            log.info("[SSE] 신규 세션 발급 후 연결: {}", uuid);
        }

        return sseEmitterManager.connect(uuid);
    }

    /**
     * POST /sse/close
     * 클라이언트가 명시적으로 종료 요청 시 (beforeunload 등)
     */
    @PostMapping("/close")
    public ResponseEntity<Void> close(HttpServletRequest request) {
        String uuid = resolveUuid(request);
        if (uuid != null) {
            sseEmitterManager.close(uuid);
        }
        return ResponseEntity.ok().build();
    }

    private String resolveUuid(HttpServletRequest request) {
        if (request.getCookies() == null) return null;
        for (Cookie cookie : request.getCookies()) {
            if (SESSION_COOKIE.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }
}
package com.example.orderService.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.UUID;

/**
 * 관리자 SSE 연결 엔드포인트
 * GET /admin/sse/connect
 *
 * admin.html 로드 시 자동 연결.
 * adminKey는 브라우저 탭 단위 식별자 (sessionStorage 저장).
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/sse")
public class AdminSseController {

    private final AdminSseManager adminSseManager;

    @GetMapping(value = "/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(
            @RequestParam(required = false) String adminKey) {

        // adminKey 없으면 서버에서 발급
        String key = (adminKey != null && !adminKey.isBlank())
                ? adminKey
                : UUID.randomUUID().toString();

        return adminSseManager.connect(key);
    }
}
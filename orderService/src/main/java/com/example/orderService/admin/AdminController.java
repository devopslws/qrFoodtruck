package com.example.orderService.admin;

import com.example.orderService.admin.dto.RefundRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 관리자 통합 컨트롤러
 *
 * POST /api/admin/order/receive      — 주문 수령 처리
 * GET  /api/admin/refund/lookup      — 환불 전 결제 조회
 * POST /api/admin/refund             — Toss 환불 처리
 * GET  /api/admin/sales/log          — 오늘 판매 로그
 * GET  /api/admin/sessions/snapshot  — 진행중 주문 스냅샷
 * GET  /admin/sse/connect            — 관리자 SSE 연결
 * GET  /api/admin/stock              — 재고 목록 (inventory-service 프록시)
 * POST /api/admin/stock/{productId}  — 재고 조정 (inventory-service 프록시)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;
    private final AdminSseManager adminSseManager;

    // ── 주문 수령 ──────────────────────────────────────────────

    @PostMapping("/api/admin/order/receive")
    public ResponseEntity<Void> receive(@RequestParam String orderNo) {
        adminService.receiveOrder(orderNo);
        return ResponseEntity.ok().build();
    }

    // ── 환불 ──────────────────────────────────────────────────

    @GetMapping("/api/admin/refund/lookup")
    public ResponseEntity<?> lookupRefund(@RequestParam String orderNo) {
        try {
            return ResponseEntity.ok(adminService.lookupRefund(orderNo));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/api/admin/refund")
    public ResponseEntity<?> refund(@RequestBody RefundRequest req) {
        try {
            adminService.processRefund(req.orderNo(), req.cancelAmount(), req.cancelReason());
            return ResponseEntity.ok(Map.of("message", "환불이 완료되었습니다."));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (RuntimeException e) {
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ── 판매 로그 ──────────────────────────────────────────────

    @GetMapping("/api/admin/sales/log")
    public ResponseEntity<List<Map<String, Object>>> salesLog() {
        return ResponseEntity.ok(adminService.getSalesLog());
    }

    // ── 세션 스냅샷 ────────────────────────────────────────────

    @GetMapping("/api/admin/sessions/snapshot")
    public ResponseEntity<List<Map<String, String>>> snapshot() {
        return ResponseEntity.ok(adminService.getSessionSnapshot());
    }

    // ── 관리자 SSE ─────────────────────────────────────────────

    @GetMapping(value = "/admin/sse/connect", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connect(@RequestParam(required = false) String adminKey) {
        String key = (adminKey != null && !adminKey.isBlank())
                ? adminKey
                : UUID.randomUUID().toString();
        return adminSseManager.connect(key);
    }

    // ── 재고 (inventory-service 프록시) ───────────────────────

    @GetMapping("/api/admin/stock")
    public ResponseEntity<List<Map<String, Object>>> listStock() {
        return ResponseEntity.ok(adminService.listStock());
    }

    @PostMapping("/api/admin/stock/{productId}")
    public ResponseEntity<?> adjustStock(@PathVariable Long productId,
                                         @RequestBody Map<String, Integer> body) {
        int delta = body.getOrDefault("delta", 0);
        adminService.adjustStock(productId, delta);
        return ResponseEntity.ok(Map.of("message", "재고가 조정되었습니다."));
    }
}

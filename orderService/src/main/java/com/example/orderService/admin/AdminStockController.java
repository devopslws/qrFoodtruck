package com.example.orderService.admin;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 관리자 재고 조회/조정 (inventory-service 프록시)
 *
 * GET  /api/admin/stock              — 전체 재고 목록 (productId, name, quantity)
 * POST /api/admin/stock/{productId}  — 재고 증감 조정 (delta: 양수/음수)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/stock")
public class AdminStockController {

    private final RestClient inventoryRestClient;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        List<Map<String, Object>> stocks = inventoryRestClient.get()
                .uri("/api/admin/stock")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});
        return ResponseEntity.ok(stocks);
    }

    @PostMapping("/{productId}")
    public ResponseEntity<?> adjust(@PathVariable Long productId,
                                    @RequestBody Map<String, Integer> body) {
        int delta = body.getOrDefault("delta", 0);
        log.info("[Stock] 재고 조정 요청: productId={}, delta={}", productId, delta);

        inventoryRestClient.post()
                .uri("/api/admin/stock/" + productId)
                .body(Map.of("delta", delta))
                .retrieve()
                .toBodilessEntity();

        return ResponseEntity.ok(Map.of("message", "재고가 조정되었습니다."));
    }
}
package com.example.inventoryService.admin;

import com.example.inventoryService.stock.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 관리자 재고 조회/조정 (inventory-service)
 * HTTP 바인딩만 담당 — 비즈니스 로직은 ProductService에 위임
 *
 * GET  /api/admin/stock              — 전체 재고 목록
 * POST /api/admin/stock/{productId}  — 재고 delta 조정 (Redis + DB 동기화)
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/stock")
public class AdminStockApiController {

    private final ProductService productService;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        return ResponseEntity.ok(productService.getAllStocks());
    }

    @PostMapping("/{productId}")
    public ResponseEntity<?> adjust(@PathVariable Long productId,
                                    @RequestBody Map<String, Integer> body) {
        int delta = body.getOrDefault("delta", 0);
        try {
            return ResponseEntity.ok(productService.adjustStock(productId, delta));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}

package com.example.inventoryService.admin;

import com.example.inventoryService.stock.entity.Stock;
import com.example.inventoryService.stock.repository.StockRepository;
import com.example.inventoryService.redis.StockRedisWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 관리자 재고 조회/조정 (inventory-service)
 *
 * GET  /api/admin/stock              — 전체 재고 목록
 * POST /api/admin/stock/{productId}  — 재고 delta 조정 (Redis + DB 동기화)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/admin/stock")
public class AdminStockApiController {

    private final StockRepository stockRepository;
    private final StockRedisWriter stockRedisWriter;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> list() {
        List<Map<String, Object>> result = stockRepository.findAll().stream()
                .filter(s -> !s.isDeleted())
                .map(s -> Map.<String, Object>of(
                        "productId", s.getId(),
                        "name",      s.getName(),
                        "quantity",  s.getQuantity()
                ))
                .collect(Collectors.toList());
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{productId}")
    public ResponseEntity<?> adjust(@PathVariable Long productId,
                                    @RequestBody Map<String, Integer> body) {
        int delta = body.getOrDefault("delta", 0);
        if (delta == 0)
            return ResponseEntity.badRequest().body(Map.of("error", "delta가 0입니다."));

        Stock stock = stockRepository.findById(productId).orElse(null);
        if (stock == null)
            return ResponseEntity.badRequest().body(Map.of("error", "상품을 찾을 수 없습니다."));

        // 음수 delta가 현재 재고를 초과하면 차단 (클램핑 대신 명시적 오류)
        if (delta < 0 && Math.abs(delta) > stock.getQuantity())
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "차감량(" + Math.abs(delta) + ")이 현재 재고(" + stock.getQuantity() + ")를 초과합니다."));

        if (delta > 0) stock.increase(delta);
        else           stock.decrease(Math.abs(delta));

        stockRepository.save(stock);
        // Redis 동기화
        stockRedisWriter.loadOne(stock);

        log.info("[AdminStock] 재고 조정: productId={}, delta={}, 결과={}", productId, delta, stock.getQuantity());
        return ResponseEntity.ok(Map.of("productId", productId, "quantity", stock.getQuantity()));
    }
}


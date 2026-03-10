package com.example.inventoryService.stock.controller;

import com.example.inventoryService.redis.StockRedisWriter;
import com.example.inventoryService.stock.dto.ProductResponse;
import com.example.inventoryService.redis.StockRedisReader;
import com.example.inventoryService.stock.dto.ReserveRequest;
import com.example.inventoryService.stock.dto.ReserveResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 메뉴 조회 API (inventory-service)
 * Redis에서 전시 중인 상품만 읽어서 반환
 * order-service가 RestClient로 호출
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stock")
public class StockController {

    private final StockRedisReader stockRedisReader;
    private final RedisTemplate<String, String> redisTemplate;
    private final StockRedisWriter stockRedisWriter;

    @GetMapping("/menu")
    public ResponseEntity<List<ProductResponse>> getProducts() {
        Set<String> keys = redisTemplate.keys("product:*");

        if (keys == null || keys.isEmpty()) {
            log.warn("[Menu] Redis에 상품 데이터 없음");
            return ResponseEntity.ok(List.of());
        }

        List<ProductResponse> result = new ArrayList<>();

        for (String key : keys) {
            Long productId = Long.parseLong(key.replace("product:", ""));
            Map<Object, Object> hash = stockRedisReader.getProductInfo(productId);
            if (hash.isEmpty()) continue;

            // 재고는 stock:{id} String에서 최신값 읽기
            int stock = stockRedisReader.getStock(productId);

            ProductResponse dto = ProductResponse.from(productId, hash, stock);

            // 전시 중인 상품만 포함
            if (dto.isDisplayed()) {
                result.add(dto);
            }
        }

        result.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        log.info("[Menu] 메뉴 조회 완료 — {}개", result.size());
        return ResponseEntity.ok(result);
    }

    /**
     * 재고 선차감 / 복구 API (order-service에서 호출)
     *
     * POST /api/stock/reserve  — 재고 선차감 (조회 + 차감 원자적 동시처리)
     * POST /api/stock/release  — 재고 복구 (결제 실패 시 롤백)
     */
    @PostMapping("/reserve")
    public ResponseEntity<ReserveResponse> reserve(@RequestBody ReserveRequest request) {
        log.info("[Stock] 재고 선차감 요청: {}", request.items());

        for (ReserveRequest.Item item : request.items()) {
            long remaining = stockRedisWriter.decrease(item.productId(), item.quantity());

            if (remaining < 0) {
                stockRedisWriter.restore(item.productId(), item.quantity());
                log.warn("[Stock] 재고 부족: productId={}, requested={}, remaining={}",
                        item.productId(), item.quantity(), remaining + item.quantity());

                rollbackPrevious(request.items(), item.productId());
                return ResponseEntity.ok(new ReserveResponse(false, item.productId()));
            }
        }

        log.info("[Stock] 재고 선차감 성공");
        return ResponseEntity.ok(new ReserveResponse(true, null));
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(@RequestBody ReserveRequest request) {
        log.info("[Stock] 재고 복구 요청: {}", request.items());

        for (ReserveRequest.Item item : request.items()) {
            stockRedisWriter.restore(item.productId(), item.quantity());
        }

        return ResponseEntity.ok().build();
    }

    private void rollbackPrevious(List<ReserveRequest.Item> items, Long failedProductId) {
        for (ReserveRequest.Item item : items) {
            if (item.productId().equals(failedProductId)) break;
            stockRedisWriter.restore(item.productId(), item.quantity());
            log.info("[Stock] 롤백: productId={}, quantity={}", item.productId(), item.quantity());
        }
    }
}
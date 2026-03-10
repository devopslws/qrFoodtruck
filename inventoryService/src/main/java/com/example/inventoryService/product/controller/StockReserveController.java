package com.example.inventoryService.product.controller;

import com.example.inventoryService.redis.StockRedisWriter;
import com.example.inventoryService.product.dto.ReserveRequest;
import com.example.inventoryService.product.dto.ReserveResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 재고 선차감 / 복구 API (order-service에서 호출)
 *
 * POST /api/stock/reserve  — 재고 선차감 (조회 + 차감 원자적 동시처리)
 * POST /api/stock/release  — 재고 복구 (결제 실패 시 롤백)
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stock")
public class StockReserveController {

    private final StockRedisWriter stockRedisWriter;

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
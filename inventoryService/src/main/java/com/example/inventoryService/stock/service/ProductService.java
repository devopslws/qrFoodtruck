package com.example.inventoryService.stock.service;

import com.example.inventoryService.redis.StockRedisReader;
import com.example.inventoryService.redis.StockRedisWriter;
import com.example.inventoryService.stock.dto.ProductResponse;
import com.example.inventoryService.stock.dto.ReserveRequest;
import com.example.inventoryService.stock.dto.ReserveResponse;
import com.example.inventoryService.stock.entity.Stock;
import com.example.inventoryService.stock.repository.StockRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ProductService {

    private final StockRepository stockRepository;
    private final StockRedisReader stockRedisReader;
    private final StockRedisWriter stockRedisWriter;
    private final RedisTemplate<String, String> redisTemplate;

    /**
     * 전시 중인 메뉴 목록 조회 (Redis 기반, id 오름차순)
     */
    public List<ProductResponse> getDisplayedProducts() {
        Set<String> keys = redisTemplate.keys("product:*");

        if (keys == null || keys.isEmpty()) {
            log.warn("[Menu] Redis에 상품 데이터 없음");
            return List.of();
        }

        List<ProductResponse> result = new ArrayList<>();

        for (String key : keys) {
            Long productId = Long.parseLong(key.replace("product:", ""));
            Map<Object, Object> hash = stockRedisReader.getProductInfo(productId);
            if (hash.isEmpty()) continue;

            int stock = stockRedisReader.getStock(productId);
            ProductResponse dto = ProductResponse.from(productId, hash, stock);

            if (dto.isDisplayed()) {
                result.add(dto);
            }
        }

        result.sort((a, b) -> Long.compare(a.getId(), b.getId()));
        log.info("[Menu] 메뉴 조회 완료 — {}개", result.size());
        return result;
    }

    /**
     * 재고 선차감 — 원자적 DECR, 부족 시 이전 항목 rollback
     */
    public ReserveResponse reserveStock(ReserveRequest request) {
        log.info("[Stock] 재고 선차감 요청: {}", request.items());

        for (ReserveRequest.Item item : request.items()) {
            long remaining = stockRedisWriter.decrease(item.productId(), item.quantity());

            if (remaining < 0) {
                stockRedisWriter.restore(item.productId(), item.quantity());
                log.warn("[Stock] 재고 부족: productId={}, requested={}, remaining={}",
                        item.productId(), item.quantity(), remaining + item.quantity());

                rollbackPrevious(request.items(), item.productId());
                return new ReserveResponse(false, item.productId());
            }
        }

        log.info("[Stock] 재고 선차감 성공");
        return new ReserveResponse(true, null);
    }

    /**
     * 재고 복구 — 결제 실패 시 롤백
     */
    public void releaseStock(ReserveRequest request) {
        log.info("[Stock] 재고 복구 요청: {}", request.items());

        for (ReserveRequest.Item item : request.items()) {
            stockRedisWriter.restore(item.productId(), item.quantity());
        }
    }

    /**
     * 전체 재고 목록 조회 (DB, deleted=false)
     */
    public List<Map<String, Object>> getAllStocks() {
        return stockRepository.findAll().stream()
                .filter(s -> !s.isDeleted())
                .map(s -> Map.<String, Object>of(
                        "productId", s.getId(),
                        "name",      s.getName(),
                        "quantity",  s.getQuantity()
                ))
                .collect(Collectors.toList());
    }

    /**
     * 재고 delta 조정 (DB + Redis 동기화)
     *
     * @throws IllegalArgumentException delta=0, 상품 미존재, 차감량 초과 시
     */
    public Map<String, Object> adjustStock(Long productId, int delta) {
        if (delta == 0)
            throw new IllegalArgumentException("delta가 0입니다.");

        Stock stock = stockRepository.findById(productId)
                .orElseThrow(() -> new IllegalArgumentException("상품을 찾을 수 없습니다."));

        if (delta < 0 && Math.abs(delta) > stock.getQuantity())
            throw new IllegalArgumentException(
                    "차감량(" + Math.abs(delta) + ")이 현재 재고(" + stock.getQuantity() + ")를 초과합니다.");

        if (delta > 0) stock.increase(delta);
        else           stock.decrease(Math.abs(delta));

        stockRepository.save(stock);
        stockRedisWriter.loadOne(stock);

        log.info("[AdminStock] 재고 조정: productId={}, delta={}, 결과={}", productId, delta, stock.getQuantity());
        return Map.of("productId", productId, "quantity", stock.getQuantity());
    }

    private void rollbackPrevious(List<ReserveRequest.Item> items, Long failedProductId) {
        for (ReserveRequest.Item item : items) {
            if (item.productId().equals(failedProductId)) break;
            stockRedisWriter.restore(item.productId(), item.quantity());
            log.info("[Stock] 롤백: productId={}, quantity={}", item.productId(), item.quantity());
        }
    }
}

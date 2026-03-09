package com.example.inventoryService.product.controller;

import com.example.inventoryService.product.dto.ProductResponse;
import com.example.inventoryService.redis.StockRedisReader;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
@RequestMapping("/api/products")
public class ProductController {

    private final StockRedisReader stockRedisReader;
    private final RedisTemplate<String, String> redisTemplate;

    @GetMapping
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
}
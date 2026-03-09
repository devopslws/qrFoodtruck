package com.example.inventoryService.common;

import com.example.inventoryService.product.entity.Product;
import com.example.inventoryService.product.repository.ProductRepository;
import com.example.inventoryService.redis.StockInitializer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 운영 데이터 초기화 컨트롤러
 * POST /reset → 전체 상품 데이터 삭제 후 시드 데이터 재삽입 + Redis 재적재
 *
 * 주의: 운영 환경에서는 관리자만 호출할 것
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/reset")
public class DataResetController {

    private final ProductRepository productRepository;
    private final StockInitializer stockInitializer;

    @PostMapping
    public ResponseEntity<String> reset() throws Exception {
        log.warn("[RESET] 재고 초기화 시작");

        // 1. 전체 상품 삭제
        productRepository.deleteAll();
        log.info("[RESET] 기존 상품 데이터 삭제 완료");

        // 2. 시드 데이터 삽입
        productRepository.saveAll(seedProducts());
        log.info("[RESET] 시드 데이터 삽입 완료");

        // 3. Redis 재적재 (StockInitializer.run() 재호출)
        stockInitializer.run(null);
        log.info("[RESET] Redis 재적재 완료");

        return ResponseEntity.ok("데이터 초기화 완료");
    }

    private List<Product> seedProducts() {
        return List.of(
                Product.of("짜장면",  8000,  5,  true,  false),
                Product.of("짬뽕",   10000,  5,  true,  false),
                Product.of("탕수육", 18000,  0,  true,  true),
                Product.of("군만두",  6000,  20, false, false),
                Product.of("한정판 세트", 35000, 1, true, false)
        );
    }
}
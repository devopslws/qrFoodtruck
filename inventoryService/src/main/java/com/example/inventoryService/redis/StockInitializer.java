package com.example.inventoryService.redis;

import com.example.inventoryService.product.entity.Product;
import com.example.inventoryService.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 서버 시작 시 DB의 재고 데이터를 Redis에 적재
 *
 * ApplicationRunner: Spring Context 완전히 뜬 후 실행됨
 * → DB 연결, JPA, RedisTemplate 등 모든 Bean이 준비된 상태에서 안전하게 실행
 *
 * [적재 전략]
 * - ECS Fargate로 인스턴스를 늘릴 경우 각 인스턴스가 시작할 때마다 실행됨
 * - Redis는 공유 저장소이므로 중복 적재가 발생할 수 있음
 * - 멱등성 보장: SET 명령은 같은 값을 덮어쓰므로 여러 번 실행해도 안전
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class StockInitializer implements ApplicationRunner {

    private final ProductRepository productRepository;
    private final StockRedisWriter stockRedisWriter;

    @Override
    public void run(ApplicationArguments args) {
        log.info("[Redis] 재고 초기 적재 시작...");

        // isDeleted = false인 상품만 조회 (@SQLRestriction 적용됨)
        List<Product> products = productRepository.findAll();

        if (products.isEmpty()) {
            log.warn("[Redis] 적재할 상품이 없습니다. DB 시드 데이터를 확인하세요.");
            return;
        }

        stockRedisWriter.loadAll(products);

        log.info("[Redis] 재고 초기 적재 완료 — 상품 {}개", products.size());
    }
}
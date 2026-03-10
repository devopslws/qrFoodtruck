package com.example.orderService.message.inventory.stock;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

/**
 * 메뉴 조회 프록시 (order-service)
 * 고객 화면 → order-service → inventory-service
 * CORS 없이 단일 origin으로 처리
 */
@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/products")
public class StockProxyController {

    private final RestClient inventoryRestClient;

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> getProducts() {
        log.info("[Menu] inventory-service 메뉴 조회 요청");

        List<Map<String, Object>> products = inventoryRestClient.get()
                .uri("/api/products")
                .retrieve()
                .body(new ParameterizedTypeReference<>() {});

        return ResponseEntity.ok(products);
    }
}
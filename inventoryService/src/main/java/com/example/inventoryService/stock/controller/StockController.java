package com.example.inventoryService.stock.controller;

import com.example.inventoryService.stock.dto.ProductResponse;
import com.example.inventoryService.stock.dto.ReserveRequest;
import com.example.inventoryService.stock.dto.ReserveResponse;
import com.example.inventoryService.stock.service.ProductService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 메뉴 조회 / 재고 선차감·복구 API (inventory-service)
 * HTTP 바인딩만 담당 — 비즈니스 로직은 ProductService에 위임
 */
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/stock")
public class StockController {

    private final ProductService productService;

    @GetMapping("/menu")
    public ResponseEntity<List<ProductResponse>> getProducts() {
        return ResponseEntity.ok(productService.getDisplayedProducts());
    }

    @PostMapping("/reserve")
    public ResponseEntity<ReserveResponse> reserve(@RequestBody ReserveRequest request) {
        return ResponseEntity.ok(productService.reserveStock(request));
    }

    @PostMapping("/release")
    public ResponseEntity<Void> release(@RequestBody ReserveRequest request) {
        productService.releaseStock(request);
        return ResponseEntity.ok().build();
    }
}

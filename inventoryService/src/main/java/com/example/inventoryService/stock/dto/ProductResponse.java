package com.example.inventoryService.stock.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.Map;

@Getter
@Builder
public class ProductResponse {

    private Long id;
    private String name;
    private int price;
    private int stock;
    private boolean displayed;

    public static ProductResponse from(Long id, Map<Object, Object> hash, int stock) {
        return ProductResponse.builder()
                .id(id)
                .name((String) hash.get("name"))
                .price(Integer.parseInt((String) hash.getOrDefault("price", "0")))
                .stock(stock)
                .displayed(Boolean.parseBoolean((String) hash.getOrDefault("isDisplayed", "false")))
                .build();
    }
}
package com.example.inventoryService.redis;

/**
 * Redis 키 상수 모음
 *
 * [키 구조]
 * product:{id}    Hash   - 메뉴 조회용 (name, price, quantity, isDisplayed)
 * stock:{id}      String - 주문 시 재고 검증·차감용 (원자적 DECR)
 * displayed:{id}  String - 주문 시 전시 여부 검증용
 *
 * product Hash와 stock/displayed String을 분리한 이유:
 * - 조회(Hash)와 차감(String DECR)을 분리해 각 연산의 원자성을 보장
 * - Hash의 HINCRBY도 원자적이지만, String DECR이 의도를 더 명확하게 표현
 */
public class RedisKeyConstants {

    private RedisKeyConstants() {}

    // Hash: 메뉴 전체 정보 (조회용)
    public static final String PRODUCT_KEY = "product:";

    // String: 재고 수량 (차감·검증용)
    public static final String STOCK_KEY = "stock:";

    // String: 전시 여부 (검증용)
    public static final String DISPLAYED_KEY = "displayed:";

    // Hash 필드명
    public static final String FIELD_NAME        = "name";
    public static final String FIELD_PRICE       = "price";
    public static final String FIELD_QUANTITY    = "quantity";
    public static final String FIELD_DISPLAYED   = "isDisplayed";

    public static String productKey(Long productId) {
        return PRODUCT_KEY + productId;
    }

    public static String stockKey(Long productId) {
        return STOCK_KEY + productId;
    }

    public static String displayedKey(Long productId) {
        return DISPLAYED_KEY + productId;
    }
}
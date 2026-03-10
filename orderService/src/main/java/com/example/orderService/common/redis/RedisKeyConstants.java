package com.example.orderService.common.redis;

/**
 * Redis 키 상수 (order-service)
 * inventory-service가 Write한 키를 동일한 규칙으로 Read
 */
public class RedisKeyConstants {

    private RedisKeyConstants() {}

    // inventory-service가 적재한 키 (Read Only)
    public static final String PRODUCT_KEY   = "product:";
    public static final String STOCK_KEY     = "stock:";
    public static final String DISPLAYED_KEY = "displayed:";

    // order-service가 직접 관리하는 키
    public static final String SESSION_KEY   = "session:";
    public static final String CART_KEY      = "cart:";

    // Hash 필드명
    public static final String FIELD_NAME      = "name";
    public static final String FIELD_PRICE     = "price";
    public static final String FIELD_QUANTITY  = "quantity";
    public static final String FIELD_DISPLAYED = "isDisplayed";

    public static String productKey(Long productId)   { return PRODUCT_KEY + productId; }
    public static String stockKey(Long productId)     { return STOCK_KEY + productId; }
    public static String displayedKey(Long productId) { return DISPLAYED_KEY + productId; }
    public static String sessionKey(String uuid)      { return SESSION_KEY + uuid; }
    public static String cartKey(String uuid)         { return CART_KEY + uuid; }
}
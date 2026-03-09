package com.example.orderService.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "order_items")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class OrderItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Orders orders;

    /** 주문 시점의 상품 식별자 (서버 B product PK, FK 제약 없음) */
    @Column(nullable = false)
    private Long productId;

    /** 주문 시점 스냅샷 — 이후 상품 정보가 바뀌어도 영향 없음 */
    @Column(nullable = false, length = 255)
    private String productName;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(nullable = false)
    private Integer quantity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderItemStatus status;

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum OrderItemStatus {
        ACCEPTED,    // 접수
        PAID,        // 결제완료
        FULL_REFUND  // 전액환불
    }

    @Builder
    public OrderItem(Orders orders, Long productId, String productName,
                     BigDecimal unitPrice, Integer quantity, OrderItemStatus status) {
        this.orders = orders;
        this.productId = productId;
        this.productName = productName;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
        this.status = status;
    }

    public void updateStatus(OrderItemStatus status) {
        this.status = status;
    }

    public void softDelete() {
        this.isDeleted = true;
    }
}
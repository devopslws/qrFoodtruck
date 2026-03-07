package com.example.orderService.domain.order.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Orders {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Redis에 저장된 익명 고객 세션 UUID */
    @Column(nullable = false, length = 36)
    private String customerId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal paidPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @OneToMany(mappedBy = "orders", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> orderItems = new ArrayList<>();

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

    public enum OrderStatus {
        ACCEPTED,       // 주문접수
        PAID,           // 결제완료
        PARTIAL_REFUND, // 부분환불
        FULL_REFUND     // 전액환불
    }

    @Builder
    public Orders(String customerId, BigDecimal totalPrice, BigDecimal paidPrice, OrderStatus status) {
        this.customerId = customerId;
        this.totalPrice = totalPrice;
        this.paidPrice = paidPrice;
        this.status = status;
    }

    public void updateStatus(OrderStatus status) {
        this.status = status;
    }

    public void softDelete() {
        this.isDeleted = true;
    }
}
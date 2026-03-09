package com.example.orderService.payment.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * PG사(Toss)에 제출하는 주문번호.
     * 내부 PK 대신 이 값을 외부에 노출.
     */
    @Column(nullable = false, unique = true, length = 36, updatable = false)
    private String orderNo;

    /** 주문과의 연결 (MSA — FK 제약 없이 타입만 일치) */
    @Column(nullable = false)
    private Long orderId;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal totalPrice;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal paidPrice;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PaymentType paymentType;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PaymentStatus status;

    @OneToMany(mappedBy = "payment", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<PaymentDetail> paymentDetails = new ArrayList<>();

    @Column(nullable = false)
    private boolean isDeleted = false;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        this.orderNo = UUID.randomUUID().toString();
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public enum PaymentType {
        CARD,           // 카드
        ACCOUNT_TRANSFER // 계좌이체
    }

    public enum PaymentStatus {
        PENDING,        // 입금대기
        COMPLETED,      // 납부완료
        PARTIAL_REFUND, // 부분환불
        FULL_REFUND     // 전액환불
    }

    @Builder
    public Payment(Long orderId, BigDecimal totalPrice, BigDecimal paidPrice,
                   PaymentType paymentType, PaymentStatus status) {
        this.orderId = orderId;
        this.totalPrice = totalPrice;
        this.paidPrice = paidPrice;
        this.paymentType = paymentType;
        this.status = status;
    }

    public void updateStatus(PaymentStatus status) {
        this.status = status;
    }

    public void softDelete() {
        this.isDeleted = true;
    }
}
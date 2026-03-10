package com.example.orderService.payment.entity;

import com.example.orderService.order.entity.Orders;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "payments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false, unique = true)
    private Orders order;

    @Column(nullable = false)
    private String paymentKey;

    @Column(nullable = false)
    private String status; // COMPLETED / FAILED

    @Column(nullable = false)
    private int paidAmount;

    private LocalDateTime paidAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        paidAt = LocalDateTime.now();
    }

    public void cancel() {
        this.status = "CANCELLED";
    }
}
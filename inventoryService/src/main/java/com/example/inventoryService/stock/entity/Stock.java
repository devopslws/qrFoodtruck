package com.example.inventoryService.stock.entity;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "stock")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Builder
@AllArgsConstructor
public class Stock {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private int price;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false)
    private boolean displayed;

    @Column(nullable = false)
    private boolean deleted;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public static Stock of(String name, int price, int quantity, boolean displayed, boolean deleted) {
        return Stock.builder()
                .name(name)
                .price(price)
                .quantity(quantity)
                .displayed(displayed)
                .deleted(deleted)
                .build();
    }

    public void decrease(int amount) {
        this.quantity = Math.max(0, this.quantity - amount);
    }

    public void increase(int amount) {
        this.quantity += amount;
    }
}
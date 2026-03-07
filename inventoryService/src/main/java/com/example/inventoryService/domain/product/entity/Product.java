package com.example.inventoryService.domain.product.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "products")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal price;

    @Column(nullable = false)
    private Integer stockQuantity;

    /** false면 품절/미전시 처리 */
    @Column(nullable = false)
    private boolean isDisplayed = true;

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

    @Builder
    public Product(String name, BigDecimal price, Integer stockQuantity, boolean isDisplayed) {
        this.name = name;
        this.price = price;
        this.stockQuantity = stockQuantity;
        this.isDisplayed = isDisplayed;
    }

    public void deductStock(int quantity) {
        if (this.stockQuantity < quantity) {
            throw new IllegalStateException("재고가 부족합니다.");
        }
        this.stockQuantity -= quantity;
    }

    public void restoreStock(int quantity) {
        this.stockQuantity += quantity;
    }

    public void updateDisplayed(boolean isDisplayed) {
        this.isDisplayed = isDisplayed;
    }

    public void softDelete() {
        this.isDeleted = true;
    }
}
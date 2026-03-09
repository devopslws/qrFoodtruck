package com.example.inventoryService.manufacture.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@Table(name = "manufactures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@SQLRestriction("is_deleted = false")
public class Manufacture {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 서버 A의 orders.id — MSA구조라 FK 제약 없이 타입만 일치 */
    @Column(nullable = false)
    private Long orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ManufactureStatus status;

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

    public enum ManufactureStatus {
        ACCEPTED,       // 접수
        COMPLETED,      // 제조완료
        DELIVERED,      // 전달완료
        DISCARDED       // 반환폐기 (조리 중 취소 or 조리 완료 후 컴플레인 환불)
    }

    @Builder
    public Manufacture(Long orderId, ManufactureStatus status) {
        this.orderId = orderId;
        this.status = status;
    }

    public void updateStatus(ManufactureStatus status) {
        this.status = status;
    }

    public void softDelete() {
        this.isDeleted = true;
    }
}
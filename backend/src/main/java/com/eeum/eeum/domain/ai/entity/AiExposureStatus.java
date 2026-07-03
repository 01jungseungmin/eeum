package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.ai.enums.AiCustomerType;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_exposure_status", uniqueConstraints = {
        @UniqueConstraint(name = "uk_ai_exposure_store", columnNames = "store_id")
})
public class AiExposureStatus extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_exposure_status_id")
    private Long aiExposureStatusId;

    // start/stop/updateConditions 동시 쓰기 시 lost update 방지
    // columnDefinition에 DEFAULT 0을 명시해 ddl-auto:update 시 기존 행도 0으로 채워짐
    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long version;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "started_at")
    private LocalDateTime startedAt;

    @Column(name = "stopped_at")
    private LocalDateTime stoppedAt;

    @Column(name = "target_count", nullable = false)
    private int targetCount;

    @Column(name = "radius_km", nullable = false)
    private double radiusKm;

    @Column(name = "interest", length = 50)
    private String interest;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private AiCustomerType customerType;

    public static AiExposureStatus init(Store store) {
        AiExposureStatus status = new AiExposureStatus();
        status.store = store;
        status.active = false;
        status.targetCount = 0;
        status.radiusKm = 1.5;
        status.customerType = AiCustomerType.ALL;
        return status;
    }

    public void updateConditions(Double radiusKm, String interest, AiCustomerType customerType, int targetCount) {
        if (radiusKm != null) {
            this.radiusKm = radiusKm;
        }
        if (interest != null) {
            this.interest = interest;
        }
        if (customerType != null) {
            this.customerType = customerType;
        }
        this.targetCount = targetCount;
    }

    public void start(LocalDateTime now, int targetCount) {
        if (this.active) {
            throw new BusinessException(ErrorCode.AI_INVALID_STATUS);
        }
        this.active = true;
        this.startedAt = now;
        this.stoppedAt = null;
        this.targetCount = targetCount;
    }

    public void stop(LocalDateTime now) {
        if (!this.active) {
            throw new BusinessException(ErrorCode.AI_INVALID_STATUS);
        }
        this.active = false;
        this.stoppedAt = now;
    }
}

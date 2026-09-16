package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 생활권 광고 노출 로그 — 노출 수 집계용 (리포트 고도화는 3차)
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_ad_exposure_log", indexes = {
        @Index(name = "idx_ai_ad_exposure_store", columnList = "store_id, exposed_at")
})
public class AiAdExposureLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_ad_exposure_log_id")
    private Long aiAdExposureLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "exposure_status_id", nullable = false)
    private Long exposureStatusId;

    // 비로그인 노출은 null — 개인정보 최소화 (지역/관심사 원본은 저장하지 않음)
    @Column(name = "viewer_account_id")
    private Long viewerAccountId;

    @Column(name = "exposed_at", nullable = false)
    private LocalDateTime exposedAt;

    @Column(name = "source", length = 30)
    private String source;

    @Column(name = "request_id", length = 64)
    private String requestId;

    public static AiAdExposureLog record(Store store, Long exposureStatusId, Long viewerAccountId,
                                         String source, String requestId) {
        AiAdExposureLog log = new AiAdExposureLog();
        log.store = store;
        log.exposureStatusId = exposureStatusId;
        log.viewerAccountId = viewerAccountId;
        log.exposedAt = LocalDateTime.now();
        log.source = source;
        log.requestId = requestId;
        return log;
    }
}

package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 생활권 광고 클릭 로그 — 전환 추적의 campaign/exposure 연결 고리
@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_ad_click_log", indexes = {
        @Index(name = "idx_ai_ad_click_store", columnList = "store_id, clicked_at")
})
public class AiAdClickLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_ad_click_log_id")
    private Long aiAdClickLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @Column(name = "exposure_status_id", nullable = false)
    private Long exposureStatusId;

    @Column(name = "viewer_account_id")
    private Long viewerAccountId;

    @Column(name = "clicked_at", nullable = false)
    private LocalDateTime clickedAt;

    @Column(name = "request_id", length = 64)
    private String requestId;

    public static AiAdClickLog record(Store store, Long exposureStatusId, Long viewerAccountId, String requestId) {
        AiAdClickLog log = new AiAdClickLog();
        log.store = store;
        log.exposureStatusId = exposureStatusId;
        log.viewerAccountId = viewerAccountId;
        log.clickedAt = LocalDateTime.now();
        log.requestId = requestId;
        return log;
    }
}

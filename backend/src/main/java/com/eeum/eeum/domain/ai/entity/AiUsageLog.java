package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.enums.AiUsageType;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_usage_log", indexes = {
        @Index(name = "idx_ai_usage_store_month", columnList = "store_id, usage_year_month")
})
// 월별 AI 생성 기능 사용량 기록
public class AiUsageLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_usage_log_id")
    private Long aiUsageLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_account_id", nullable = false)
    private Account ownerAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "usage_type", nullable = false, length = 30)
    private AiUsageType usageType;

    // 형식: yyyy-MM (예: 2026-07)
    @Column(name = "usage_year_month", nullable = false, length = 7)
    private String yearMonth;

    public static AiUsageLog record(Store store, Account ownerAccount, AiUsageType usageType, String yearMonth) {
        AiUsageLog log = new AiUsageLog();
        log.store = store;
        log.ownerAccount = ownerAccount;
        log.usageType = usageType;
        log.yearMonth = yearMonth;
        return log;
    }
}

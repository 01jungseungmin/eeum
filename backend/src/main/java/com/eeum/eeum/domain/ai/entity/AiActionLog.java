package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.enums.AiActionType;
import com.eeum.eeum.domain.store.entity.Store;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ai_action_log")
// AI 매니저에서 발생한 주요 행동 기록
public class AiActionLog extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_action_log_id")
    private Long aiActionLogId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_account_id", nullable = false)
    private Account ownerAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", nullable = false, length = 30)
    private AiActionType actionType;

    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "description", length = 500)
    private String description;

    public static AiActionLog record(
            Store store,
            Account ownerAccount,
            AiActionType actionType,
            String targetType,
            Long targetId,
            String description
    ) {
        AiActionLog log = new AiActionLog();
        log.store = store;
        log.ownerAccount = ownerAccount;
        log.actionType = actionType;
        log.targetType = targetType;
        log.targetId = targetId;
        log.description = description;
        return log;
    }
}

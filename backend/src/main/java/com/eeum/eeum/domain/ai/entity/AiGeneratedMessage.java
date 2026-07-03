package com.eeum.eeum.domain.ai.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.ai.enums.AiCareType;
import com.eeum.eeum.domain.ai.enums.AiChannel;
import com.eeum.eeum.domain.ai.enums.AiMessageStatus;
import com.eeum.eeum.domain.ai.enums.AiMessageType;
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
@Table(name = "ai_generated_message")
//AI가 생성한 모든 문구 및 초안
public class AiGeneratedMessage extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ai_generated_message_id")
    private Long aiGeneratedMessageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "store_id", nullable = false)
    private Store store;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "owner_account_id", nullable = false)
    private Account ownerAccount;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false, length = 30)
    private AiMessageType type;

    // Polymorphic 참조 — FK 없이 대상 유형/ID만 저장 (예: STORE_REVIEW + reviewId)
    @Column(name = "target_type", length = 30)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "title", length = 200)
    private String title;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    // AI가 최초 생성한 원본 문구 (수정 이력 비교용)
    @Column(name = "original_content", nullable = false, columnDefinition = "TEXT")
    private String originalContent;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AiMessageStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", length = 20)
    private AiChannel channel;

    @Column(name = "scheduled_at")
    private LocalDateTime scheduledAt;

    @Column(name = "sent_at")
    private LocalDateTime sentAt;

    // 상태 전이 낙관적 락 — Redis 락 해제~트랜잭션 커밋 사이 틈에서 발생할 수 있는
    // 중복 발송/중복 StoreNotice 생성을 DB 레벨에서 차단 (충돌 시 두 번째 트랜잭션 롤백)
    // columnDefinition에 DEFAULT 0을 명시해 ddl-auto:update 시 기존 행도 0으로 채워짐
    @Version
    @Column(name = "version", nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long version;

    public static AiGeneratedMessage createDraft(
            Store store,
            Account ownerAccount,
            AiMessageType type,
            String targetType,
            Long targetId,
            String title,
            String content,
            AiChannel channel
    ) {
        AiGeneratedMessage message = new AiGeneratedMessage();
        message.store = store;
        message.ownerAccount = ownerAccount;
        message.type = type;
        message.targetType = targetType;
        message.targetId = targetId;
        message.title = title;
        message.content = content;
        message.originalContent = content;
        message.status = AiMessageStatus.DRAFT;
        message.channel = channel;
        return message;
    }

    public void edit(String title, String content) {
        if (!isEditable()) {
            throw new BusinessException(ErrorCode.AI_MESSAGE_NOT_EDITABLE);
        }
        if (title != null) {
            this.title = title;
        }
        if (content != null) {
            this.content = content;
        }
        // 예약된 메시지를 수정하면 예약 해제 — 수정 후 다시 보내기/예약하기 필요
        if (this.status == AiMessageStatus.SCHEDULED) {
            this.scheduledAt = null;
        }
        this.status = AiMessageStatus.REVIEWED;
    }

    public void send(LocalDateTime now) {
        validateTransitable();
        this.status = AiMessageStatus.SENT;
        this.sentAt = now;
        this.scheduledAt = null;
    }

    public void schedule(LocalDateTime scheduledAt, LocalDateTime now) {
        validateTransitable();
        if (scheduledAt == null || !scheduledAt.isAfter(now)) {
            throw new BusinessException(ErrorCode.AI_INVALID_SCHEDULE_TIME);
        }
        this.status = AiMessageStatus.SCHEDULED;
        this.scheduledAt = scheduledAt;
    }

    public void cancel() {
        validateTransitable();
        this.status = AiMessageStatus.CANCELLED;
        this.scheduledAt = null;
    }

    public boolean isOwnedBy(Long accountId) {
        return this.ownerAccount.getAccountId().equals(accountId);
    }

    // targetType은 String으로 저장되므로 이 메서드를 통해서만 비교 — 상수명 변경 시 이 곳만 수정하면 됨
    public boolean hasCareType(AiCareType careType) {
        return careType.name().equals(this.targetType);
    }

    private boolean isEditable() {
        return this.status == AiMessageStatus.DRAFT
                || this.status == AiMessageStatus.REVIEWED
                || this.status == AiMessageStatus.SCHEDULED;
    }

    private void validateTransitable() {
        // 화이트리스트: 검토 완료(REVIEWED) 또는 예약됨(SCHEDULED) 상태만 전이 허용
        // DRAFT는 직접 발송 불가 — edit() 을 통해 REVIEWED로 전환 후 발송해야 함
        if (this.status == AiMessageStatus.REVIEWED || this.status == AiMessageStatus.SCHEDULED) {
            return;
        }
        if (this.status == AiMessageStatus.SENT) {
            throw new BusinessException(ErrorCode.AI_MESSAGE_ALREADY_SENT);
        }
        throw new BusinessException(ErrorCode.AI_INVALID_STATUS);
    }
}

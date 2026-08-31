package com.eeum.eeum.domain.notification.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.notification.enums.OutboxStatus;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 알림 생성 요청을 원 트랜잭션과 함께 커밋해 두는 outbox.
 *
 * <p>예전에는 알림 생성을 {@code AFTER_COMMIT} + {@code @Async}로 처리했다.
 * 비동기 풀이 포화되면 그 작업이 버려져 알림이 아예 만들어지지 않았고, 원 요청은 성공으로 끝나
 * 아무도 알아채지 못했다. 커밋된 사실(메시지 전송)과 그에 따른 알림 사이에 보장이 없었다.
 *
 * <p>이 행은 메시지 저장과 <b>같은 트랜잭션</b>에서 커밋된다. 이후 처리는 스케줄러가
 * 이 행을 읽어서 하므로, 비동기 풀이 포화되든 인스턴스가 죽든 기록은 남는다.
 *
 * <p>중복 방지는 상태 전이를 알림 생성과 같은 트랜잭션에 묶어서 한다 — 처리 도중 죽으면
 * 둘 다 롤백돼 다음 주기에 다시 시도한다. 알림만 만들어지고 DONE을 못 남기는 창이 없다.
 */
@Entity
@Table(
        name = "notification_outbox",
        indexes = {
                // 대기 행 조회 전용 — 상태로 좁히고 오래된 것부터, PK로 tie-break.
                @Index(name = "idx_outbox_status_created",
                        columnList = "status, created_at, outbox_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationOutbox extends BaseEntity {

    private static final int MAX_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    // 어떤 처리기로 보낼지. 문자열로 두어 이벤트 클래스 이동·리네임에 깨지지 않게 한다.
    @Column(name = "event_type", nullable = false, length = 100)
    private String eventType;

    // 이벤트를 JSON으로 담는다. 엔티티가 아니라 스칼라만 담은 이벤트여야 한다 —
    // 지연 로딩 프록시나 순환 참조가 섞이면 직렬화가 깨진다.
    @Lob
    @Column(name = "payload", nullable = false)
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private OutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private int attemptCount;

    @Column(name = "last_error", length = 1000)
    private String lastError;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    public static NotificationOutbox pending(String eventType, String payload) {
        NotificationOutbox outbox = new NotificationOutbox();
        outbox.eventType = eventType;
        outbox.payload = payload;
        outbox.status = OutboxStatus.PENDING;
        outbox.attemptCount = 0;
        return outbox;
    }

    public void markDone() {
        this.status = OutboxStatus.DONE;
        this.processedAt = LocalDateTime.now();
    }

    /**
     * 실패 기록. 재시도 한도를 넘으면 FAILED로 내려 더 시도하지 않는다.
     *
     * <p>영원히 재시도하면 깨진 행 하나가 주기마다 앞자리를 차지해 뒤의 정상 알림을 막는다.
     */
    public void recordFailure(String error) {
        this.attemptCount++;
        this.lastError = error == null ? null : error.substring(0, Math.min(error.length(), 1000));
        if (this.attemptCount >= MAX_ATTEMPTS) {
            this.status = OutboxStatus.FAILED;
            this.processedAt = LocalDateTime.now();
        }
    }

    public boolean isExhausted() {
        return this.status == OutboxStatus.FAILED;
    }
}

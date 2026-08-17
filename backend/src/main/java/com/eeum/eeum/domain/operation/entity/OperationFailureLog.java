package com.eeum.eeum.domain.operation.entity;

import com.eeum.eeum.common.entity.BaseEntity;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 운영 중 발생한 실패를 한곳에 쌓는 통합 이력.
 *
 * <p>이전에는 결제 Webhook·환불·스케줄러 실패가 모두 SLF4J 로그로만 흘러가
 * "어제 환불이 몇 건 실패했나"에 답하려면 서버 로그를 뒤져야 했다.
 * 이 테이블은 그 질문에 쿼리 한 번으로 답하기 위한 것이다.
 *
 * <p>대상 참조는 polymorphic({@code refType} + {@code refId})으로 FK 없이 저장한다.
 * 실패 시점의 대상이 이후 삭제될 수 있고, 이력은 그와 무관하게 남아야 하기 때문이다.
 *
 * <p>Soft Delete 대상이 아니다. 보존 기간이 지난 행은
 * {@code OperationFailureLogCleanupScheduler}가 물리 삭제한다.
 */
@Entity
@Table(
        name = "operation_failure_log",
        indexes = {
                // 대시보드 기본 조회: 카테고리 필터 + 최신순
                @Index(name = "idx_ofl_category_created", columnList = "category, created_at"),
                // 특정 주문/결제의 실패 이력 추적
                @Index(name = "idx_ofl_ref", columnList = "ref_type, ref_id"),
                // 보존 기간 정리 스케줄러
                @Index(name = "idx_ofl_created", columnList = "created_at")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OperationFailureLog extends BaseEntity {

    /** errorMessage / payload 저장 상한 — 초과분은 잘라서 저장한다. */
    public static final int MESSAGE_MAX_LENGTH = 1000;
    public static final int PAYLOAD_MAX_LENGTH = 4000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "operation_failure_log_id")
    private Long operationFailureLogId;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false, length = 30)
    private OperationFailureCategory category;

    /** 실패한 구체 작업명. 예: {@code PaymentService.handleWebhook}, {@code OrderExpirationScheduler.expireOrders} */
    @Column(name = "operation", nullable = false, length = 200)
    private String operation;

    /** 대상 종류. 예: ORDER, PAYMENT, SCHEDULER. FK 없이 사용한다. */
    @Column(name = "ref_type", length = 50)
    private String refType;

    @Column(name = "ref_id")
    private String refId;

    /** 도메인 ErrorCode가 있으면 그 코드, 없으면 예외 클래스명. */
    @Column(name = "error_code", length = 100)
    private String errorCode;

    @Column(name = "error_message", length = MESSAGE_MAX_LENGTH)
    private String errorMessage;

    /**
     * 재현에 필요한 최소 컨텍스트(요청 본문 일부, 파라미터 등).
     * <b>민감정보는 호출부에서 마스킹한 뒤 넘긴다.</b> 이 필드는 관리자 화면에 그대로 노출된다.
     */
    @Column(name = "payload", length = PAYLOAD_MAX_LENGTH)
    private String payload;

    public static OperationFailureLog create(
            OperationFailureCategory category,
            String operation,
            String refType,
            String refId,
            String errorCode,
            String errorMessage,
            String payload
    ) {
        OperationFailureLog log = new OperationFailureLog();
        log.category = category;
        log.operation = operation;
        log.refType = refType;
        log.refId = refId;
        log.errorCode = errorCode;
        log.errorMessage = truncate(errorMessage, MESSAGE_MAX_LENGTH);
        log.payload = truncate(payload, PAYLOAD_MAX_LENGTH);
        return log;
    }

    // 예외 메시지와 외부 응답은 길이 상한이 없다. 잘라서 저장하지 않으면
    // 기록 자체가 DataIntegrityViolation으로 실패해 원래 장애까지 함께 묻힌다.
    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}

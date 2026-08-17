package com.eeum.eeum.application.operation.dto.response;

import com.eeum.eeum.domain.operation.entity.OperationFailureLog;
import com.eeum.eeum.domain.operation.enums.OperationFailureCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "운영 실패 이력")
public class OperationFailureLogResponseDto {

    @Schema(description = "실패 이력 ID")
    private Long failureLogId;

    @Schema(description = "분류 (PAYMENT_WEBHOOK·REFUND·SCHEDULER·EXTERNAL_API)")
    private OperationFailureCategory category;

    @Schema(description = "실패한 작업명", example = "PaymentService.cancelPayment")
    private String operation;

    @Schema(description = "대상 종류. FK 없는 논리 참조", example = "PAYMENT")
    private String refType;

    @Schema(description = "대상 ID")
    private String refId;

    @Schema(description = "도메인 ErrorCode 또는 예외 클래스명")
    private String errorCode;

    @Schema(description = "실패 메시지")
    private String errorMessage;

    @Schema(description = "재현용 컨텍스트. 민감정보는 기록 시점에 잘라낸 값")
    private String payload;

    @Schema(description = "발생일시")
    private LocalDateTime occurredAt;

    public static OperationFailureLogResponseDto from(OperationFailureLog log) {
        return OperationFailureLogResponseDto.builder()
                .failureLogId(log.getOperationFailureLogId())
                .category(log.getCategory())
                .operation(log.getOperation())
                .refType(log.getRefType())
                .refId(log.getRefId())
                .errorCode(log.getErrorCode())
                .errorMessage(log.getErrorMessage())
                .payload(log.getPayload())
                .occurredAt(log.getCreatedAt())
                .build();
    }
}

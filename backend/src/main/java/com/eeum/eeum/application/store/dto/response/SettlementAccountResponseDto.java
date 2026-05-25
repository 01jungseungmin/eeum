package com.eeum.eeum.application.store.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "정산 계좌 응답")
public class SettlementAccountResponseDto {

    @Schema(description = "정산 계좌 ID", example = "1")
    private Long settlementAccountId;

    @Schema(description = "은행명", example = "국민은행")
    private String bankName;

    @Schema(description = "계좌번호 (마스킹)", example = "123-****-789012")
    private String accountNumber;

    @Schema(description = "예금주", example = "홍길동")
    private String accountHolder;
}
package com.eeum.eeum.application.store.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
@Schema(description = "정산 계좌 등록/수정 요청")
public class SettlementAccountRequestDto {

    @Schema(description = "은행명", example = "국민은행")
    @NotBlank(message = "은행명은 필수입니다")
    private String bankName;

    @Schema(description = "계좌번호", example = "123-456-789012")
    @NotBlank(message = "계좌번호는 필수입니다")
    private String accountNumber;

    @Schema(description = "예금주", example = "홍길동")
    @NotBlank(message = "예금주는 필수입니다")
    private String accountHolder;
}
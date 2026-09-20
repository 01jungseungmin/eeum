package com.eeum.eeum.application.store.dto.response;

import com.eeum.eeum.domain.store.enums.Bank;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@Schema(description = "정산 계좌 은행 응답")
public class BankResponseDto {

    @Schema(description = "은행 코드(enum 상수명)", example = "KB")
    private String code;

    @Schema(description = "은행명. 정산 계좌 등록 시 bankName으로 그대로 보낸다", example = "국민은행")
    private String bankName;

    @Schema(description = "금융결제원 기관코드. 정산 자동이체 연동 전까지는 null이다", example = "null")
    private String institutionCode;

    public static BankResponseDto from(Bank bank) {
        return BankResponseDto.builder()
                .code(bank.name())
                .bankName(bank.getDisplayName())
                .institutionCode(bank.getInstitutionCode())
                .build();
    }
}

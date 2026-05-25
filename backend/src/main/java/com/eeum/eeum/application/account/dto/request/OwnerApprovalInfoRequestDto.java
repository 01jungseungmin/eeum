package com.eeum.eeum.application.account.dto.request;

import com.eeum.eeum.application.product.dto.request.ProductCreateRequestDto;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;

import java.util.List;

@Getter
@Schema(description = "사장 승인 추가 정보 입력 요청")
public class OwnerApprovalInfoRequestDto {

    @Schema(description = "상점 업종 ID", example = "1")
    @NotNull(message = "업종은 필수입니다.")
    private Long categoryId;

    @Valid
    @NotEmpty(message = "대표 메뉴는 1개 이상 등록해야 합니다.")
    @Schema(description = "대표 메뉴 목록")
    private List<ProductCreateRequestDto> representativeMenus;

    @Valid
    @NotEmpty(message = "영업시간은 필수입니다.")
    @Schema(description = "영업시간 목록")
    private List<BusinessHourRequestDto> businessHours;

    @Valid
    @NotNull(message = "정산 계좌 정보는 필수입니다.")
    @Schema(description = "정산 계좌 정보")
    private SettlementAccountRequestDto settlementAccount;
}
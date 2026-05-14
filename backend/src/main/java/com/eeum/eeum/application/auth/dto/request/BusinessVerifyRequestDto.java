package com.eeum.eeum.application.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;

@Getter
public class BusinessVerifyRequestDto {

    @Schema(description = "사업자등록번호", example = "1234567890")
    @NotBlank(message = "사업자 등록 번호는 필수입니다")
    private String businessNumber; // 사업자등록번호, 숫자 10자리

    @Schema(description = "대표자명", example = "user")
    @NotBlank(message = "대표자명은 필수입니다")
    private String ownerName; // 대표자명

    @Schema(description = "개업일자", example = "20210116")
    @NotBlank(message = "개업일자는 필수입니다")
    private String openingDate; // 개업일자 yyyyMMdd
}
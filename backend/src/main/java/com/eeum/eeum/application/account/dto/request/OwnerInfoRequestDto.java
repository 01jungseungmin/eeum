package com.eeum.eeum.application.account.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;

@Getter
@Schema(description = "사업자 정보 등록/수정 요청")
public class OwnerInfoRequestDto {

    @Schema(description = "전화번호", example = "010-1234-5678")
    @NotBlank(message = "전화번호는 필수입니다")
    @Pattern(regexp = "^01[0-9]-\\d{3,4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다")
    private String phone;

    @Schema(description = "사업자번호 (숫자만 10자리)", example = "1234567890")
    @NotBlank(message = "사업자번호는 필수입니다")
    @Pattern(regexp = "^\\d{10}$", message = "사업자번호는 숫자 10자리여야 합니다")
    private String businessNumber;
}

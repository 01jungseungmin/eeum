package com.eeum.eeum.application.auth.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@Schema(description = "사장 회원가입 요청")
public class OwnerSignupRequestDto {

    @Schema(description = "이메일", example = "owner@example.com")
    @NotBlank(message = "이메일은 필수입니다")
    @Email(message = "이메일 형식이 올바르지 않습니다")
    private String email;

    @Schema(description = "비밀번호", example = "Password1!")
    @NotBlank(message = "비밀번호는 필수입니다")
    @Pattern(
            regexp = "^(?=.*[A-Za-z])(?=.*\\d)(?=.*[@$!%*#?&])[A-Za-z\\d@$!%*#?&]{8,}$",
            message = "비밀번호는 영문, 숫자, 특수문자를 포함하여 8자 이상이어야 합니다"
    )
    private String password;

    @Schema(description = "이름", example = "홍길동")
    @NotBlank(message = "이름은 필수입니다")
    private String name;

    @Schema(description = "사장님 전화번호", example = "010-1234-5678")
    @NotBlank(message = "사장님 전화번호는 필수입니다")
    @Pattern(regexp = "^01[0-9]-\\d{3,4}-\\d{4}$", message = "전화번호 형식이 올바르지 않습니다")
    private String phone;

    @Schema(description = "사업자번호", example = "1234567890")
    @NotBlank(message = "사업자번호는 필수입니다")
    @Pattern(regexp = "^\\d{10}$", message = "사업자번호는 숫자 10자리여야 합니다")
    private String businessNumber;

    @Schema(description = "상호명", example = "맛있는 반찬가게")
    @NotBlank(message = "상호명은 필수입니다")
    private String storeName;

    @NotBlank(message = "개업일자는 필수입니다")
    @Schema(description = "개업일자", example = "20240101")
    private String openingDate;

    @Schema(description = "사업장 소재지", example = "서울 마포구 서교동 123-4")
    @NotBlank(message = "사업장 소재지는 필수입니다")
    private String storeAddress;

    @Schema(description = "사업장 전화번호", example = "02-1234-5678")
    @NotBlank(message = "사업장 전화번호는 필수입니다")
    @Pattern(
            regexp = "^(0\\d{1,2}-\\d{3,4}-\\d{4}|01[0-9]-\\d{3,4}-\\d{4})$",
            message = "사업장 전화번호 형식이 올바르지 않습니다"
    )
    private String storePhone;

    @Schema(description = "이메일 인증 토큰", example = "abc123")
    @NotBlank(message = "이메일 인증 토큰은 필수입니다")
    private String emailVerificationToken;
}

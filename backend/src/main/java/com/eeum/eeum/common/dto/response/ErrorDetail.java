package com.eeum.eeum.common.dto.response;

import com.eeum.eeum.exception.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;

@Getter
@Schema(description = "에러 상세 정보")
public class ErrorDetail {

    @Schema(description = "에러 코드", example = "ORDER_001")
    private final String code;

    @Schema(description = "에러 메시지", example = "재고가 부족합니다")
    private final String message;

    private ErrorDetail(String code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ErrorDetail of(ErrorCode errorCode) {//ErrorCode enum을 받아서 ErrorDetail 객체로 변환
        return new ErrorDetail(errorCode.getCode(), errorCode.getMessage());
    }

    public static ErrorDetail of(String code, String message) {//ErrorCode enum 없이, 직접 에러 코드와 메시지를 받아서 ErrorDetail 객체로 변환
        return new ErrorDetail(code, message);
    }
}
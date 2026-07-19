package com.eeum.eeum.exception;

import lombok.Getter;

//비즈니스 예외 기본 클래스
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final Object data; // 실패 응답에 함께 실어 보낼 상세 정보 (없으면 null — 대부분의 경우 사용하지 않음)

    public BusinessException(ErrorCode errorCode) { //ErrorCode 하나만 받아서 예외 생성
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = null;
    }

    public BusinessException(ErrorCode errorCode, String message) { //ErrorCode는 유지하되, 메시지만 상황에 맞게 변경
        super(message);
        this.errorCode = errorCode;
        this.data = null;
    }

    // 프론트가 확인 다이얼로그 등을 그리는 데 필요한 상세 정보를 함께 전달할 때 사용
    public BusinessException(ErrorCode errorCode, Object data) {
        super(errorCode.getMessage());
        this.errorCode = errorCode;
        this.data = data;
    }
}
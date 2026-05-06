package com.eeum.eeum.exception;

import lombok.Getter;

//비즈니스 예외 기본 클래스
@Getter
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;

    public BusinessException(ErrorCode errorCode) { //ErrorCode 하나만 받아서 예외 생성
        super(errorCode.getMessage());
        this.errorCode = errorCode;
    }

    public BusinessException(ErrorCode errorCode, String message) { //ErrorCode는 유지하되, 메시지만 상황에 맞게 변경
        super(message);
        this.errorCode = errorCode;
    }
}
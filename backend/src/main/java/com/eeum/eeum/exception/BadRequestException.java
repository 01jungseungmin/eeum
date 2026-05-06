package com.eeum.eeum.exception;

//400 Bad Request
public class BadRequestException extends BusinessException {
    public BadRequestException(ErrorCode errorCode) { //잘못된 요청 값이나 비즈니스 규칙 위반 요청일 때 발생하는 예외
        super(errorCode);
    }
}
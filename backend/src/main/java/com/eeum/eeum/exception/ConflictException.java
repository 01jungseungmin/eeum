package com.eeum.eeum.exception;

//409 Conflict
public class ConflictException extends BusinessException {
    public ConflictException(ErrorCode errorCode) { // 기존 데이터 상태와 충돌하는 요청일 때 발생하는 예외
        super(errorCode);
    }
}
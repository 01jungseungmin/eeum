package com.eeum.eeum.exception;

//404 Not Found
public class NotFoundException extends BusinessException {
    public NotFoundException(ErrorCode errorCode) { // 요청한 리소스를 찾을 수 없을 때 발생하는 예외
        super(errorCode);
    }
}

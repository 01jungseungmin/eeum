package com.eeum.eeum.exception;

//403 Forbidden
public class ForbiddenException extends BusinessException {
    public ForbiddenException(ErrorCode errorCode) { // 인증은 되었지만 해당 리소스에 접근 권한이 없을 때 발생하는 예외
        super(errorCode);
    }
}
package com.eeum.eeum.exception;

/** PortOne 호출 과정에서 발생한 모든 실패를 감싸는 단일 예외 타입. */
public class PortOnePaymentException extends BusinessException {

    public PortOnePaymentException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public PortOnePaymentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

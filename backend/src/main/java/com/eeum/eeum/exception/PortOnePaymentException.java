package com.eeum.eeum.exception;

/**
 * PortOne 호출 과정에서 발생한 모든 실패를 감싸는 단일 예외 타입.
 *
 * <p>클라이언트({@code PortOnePaymentClientImpl})는 외부 호출과 응답 해석만 책임지고,
 * 통신 오류·타임아웃·응답 형식 오류를 전부 이 타입으로 변환해 던진다.
 * <b>이력 기록은 하지 않는다</b> — 기록은 업무 맥락(orderId, storeId 등)을 아는
 * 서비스 계층이 한 번만 수행한다. 양쪽에서 기록하면 실패 1건이 이력 2건이 되어
 * 대시보드 집계와 알람 임계치가 전부 두 배로 어긋난다.
 *
 * <p>{@code RestClientException}만 잡던 시절에는 예상 밖의 {@code RuntimeException}이
 * 변환되지 않은 채 올라갔다. 클라이언트가 모든 예외를 이 타입으로 모아 주면
 * 호출부는 "PortOne에서 실패했다"를 한 가지 방법으로만 판단하면 된다.
 */
public class PortOnePaymentException extends BusinessException {

    public PortOnePaymentException(ErrorCode errorCode, String message, Throwable cause) {
        super(errorCode, message, cause);
    }

    public PortOnePaymentException(ErrorCode errorCode, String message) {
        super(errorCode, message);
    }
}

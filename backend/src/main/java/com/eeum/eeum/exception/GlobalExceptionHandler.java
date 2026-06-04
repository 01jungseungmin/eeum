package com.eeum.eeum.exception;

import com.eeum.eeum.common.dto.response.ApiResponse; //공통 응답 객체
import lombok.extern.slf4j.Slf4j; //로그
import org.springframework.http.HttpStatus; //HTTP 상태 코드
import org.springframework.http.ResponseEntity; //응답 객체 생성
import org.springframework.web.bind.MethodArgumentNotValidException; //@Valid 검증 실패를 처리하기 위한 클래스
import org.springframework.web.bind.annotation.ExceptionHandler; //특정 예외 타입을 처리하는 메서드 지정
import org.springframework.web.bind.annotation.RestControllerAdvice; //모든 Controller에서 발생한 예외를 잡는 클래스

import java.util.stream.Collectors;

@Slf4j //로그 사용
@RestControllerAdvice //모든 @RestController에서 발생한 예외를 전역으로 처리하는 클래스
public class GlobalExceptionHandler {

    // ===================== 비즈니스 예외 =====================

    @ExceptionHandler(BusinessException.class) //Spring은 예외 발생 시 가장 구체적인 타입부터 찾고 없으면 부모 타입으로 올라감
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e) {
        log.warn("[BusinessException] code={}, message={}", e.getErrorCode().getCode(), e.getMessage());

        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(ApiResponse.fail(e.getErrorCode().getCode(), e.getMessage()));
    }

    // ===================== 입력값 검증 예외 =====================

    @ExceptionHandler(MethodArgumentNotValidException.class) //MethodArgumentNotValidException이 발생 시 실행되는 메서드
    public ResponseEntity<ApiResponse<?>> handleValidationException(MethodArgumentNotValidException e) {
        String errorMessage = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage())
                .collect(Collectors.joining(", "));

        log.warn("[ValidationException] message={}", errorMessage);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.VALIDATION_INVALID_INPUT.getCode(), errorMessage));
    }

    // ===================== 서버 오류 =====================

    @ExceptionHandler(Exception.class) //위에서 따로 처리하지 않은 모든 Exception 발생 시 실행되는 메서드
    public ResponseEntity<ApiResponse<?>> handleException(Exception e) {
        log.error("[InternalServerError] message={}", e.getMessage(), e);

        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail(ErrorCode.COMMON_INTERNAL_ERROR));
    }
}
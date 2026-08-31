package com.eeum.eeum.exception;

import com.eeum.eeum.common.dto.response.ApiResponse; //공통 응답 객체
import jakarta.validation.ConstraintViolationException;
import lombok.extern.slf4j.Slf4j; //로그
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus; //HTTP 상태 코드
import org.springframework.http.ResponseEntity; //응답 객체 생성
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException; //@Valid 검증 실패를 처리하기 위한 클래스
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.bind.annotation.ExceptionHandler; //특정 예외 타입을 처리하는 메서드 지정
import org.springframework.web.bind.annotation.RestControllerAdvice; //모든 Controller에서 발생한 예외를 잡는 클래스
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j //로그 사용
@RestControllerAdvice //모든 @RestController에서 발생한 예외를 전역으로 처리하는 클래스
public class GlobalExceptionHandler {

    // ===================== 비즈니스 예외 =====================

    @ExceptionHandler(BusinessException.class) //Spring은 예외 발생 시 가장 구체적인 타입부터 찾고 없으면 부모 타입으로 올라감
    public ResponseEntity<ApiResponse<?>> handleBusinessException(BusinessException e) {
        log.warn("[BusinessException] code={}, message={}", e.getErrorCode().getCode(), e.getMessage());

        ApiResponse<?> body = e.getData() != null
                ? ApiResponse.fail(e.getErrorCode().getCode(), e.getMessage(), e.getData())
                : ApiResponse.fail(e.getErrorCode().getCode(), e.getMessage());

        return ResponseEntity
                .status(e.getErrorCode().getHttpStatus())
                .body(body);
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

    // @Validated + @Min/@Max 등 파라미터 레벨 제약 위반 시 발생
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleConstraintViolationException(ConstraintViolationException e) {
        String errorMessage = e.getConstraintViolations().stream()
                .map(v -> {
                    String path = v.getPropertyPath().toString();
                    String param = path.contains(".") ? path.substring(path.lastIndexOf('.') + 1) : path;
                    return param + ": " + v.getMessage();
                })
                .collect(Collectors.joining(", "));

        log.warn("[ConstraintViolationException] message={}", errorMessage);

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.VALIDATION_INVALID_INPUT.getCode(), errorMessage));
    }

    // 경로변수·쿼리 파라미터의 타입 변환 실패 (숫자 자리에 문자, 정의에 없는 enum 값 등).
    // 핸들러가 없으면 catch-all로 떨어져 잘못된 요청이 500 "서버 내부 오류"로 나간다.
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<ApiResponse<?>> handleTypeMismatchException(
            MethodArgumentTypeMismatchException e) {

        String errorMessage = e.getName() + ": 허용되지 않는 값입니다 (" + e.getValue() + ")";

        log.warn("[MethodArgumentTypeMismatchException] parameter={}, value={}", e.getName(), e.getValue());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.VALIDATION_INVALID_INPUT.getCode(), errorMessage));
    }

    // 필수 쿼리 파라미터 누락
    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<ApiResponse<?>> handleMissingParameterException(
            MissingServletRequestParameterException e) {

        String errorMessage = e.getParameterName() + ": 필수 파라미터입니다";

        log.warn("[MissingServletRequestParameterException] parameter={}", e.getParameterName());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(ErrorCode.VALIDATION_INVALID_INPUT.getCode(), errorMessage));
    }

    // 본문을 읽을 수 없는 경우 (깨진 JSON, 타입이 맞지 않는 필드 등).
    // 원문 메시지에는 파싱 위치·클래스명 같은 내부 정보가 담기므로 그대로 내보내지 않는다.
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiResponse<?>> handleMessageNotReadableException(
            HttpMessageNotReadableException e) {

        log.warn("[HttpMessageNotReadableException] message={}", e.getMessage());

        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(ApiResponse.fail(
                        ErrorCode.VALIDATION_INVALID_INPUT.getCode(), "요청 본문을 읽을 수 없습니다"));
    }

    // ===================== DB 제약 위반 =====================

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<?>> handleDataIntegrityViolation(DataIntegrityViolationException e) {
        log.warn("[DataIntegrityViolationException] {}", e.getMessage());
        Throwable root = e.getRootCause();
        String rootMsg = root != null ? root.getMessage() : "";
        if (rootMsg.contains("uk_reservation_table_start")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.fail(ErrorCode.RESERVATION_TABLE_UNAVAILABLE));
        }
        if (rootMsg.contains("uk_reservation_account_store_start")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.fail(ErrorCode.VISIT_RESERVATION_ALREADY_EXISTS));
        }
        // 답변 작성 요청이 동시에 두 번 들어오면 서비스단 중복 검사를 둘 다 통과한 뒤
        // 유니크 제약에서 갈린다. 진 쪽에도 "이미 답변됨"이라는 같은 의미를 돌려준다.
        if (rootMsg.contains("uk_inquiry_answer_inquiry_id")) {
            return ResponseEntity.status(HttpStatus.CONFLICT)
                    .body(ApiResponse.fail(ErrorCode.INQUIRY_ALREADY_ANSWERED));
        }
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ErrorCode.COMMON_CONFLICT));
    }

    // ===================== 낙관적 락 충돌 =====================

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<?>> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException e) {
        log.warn("[OptimisticLockingFailure] {}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail(ErrorCode.COMMON_CONFLICT));
    }

    // ===================== 존재하지 않는 URL / 지원하지 않는 메서드 =====================

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<?>> handleNoResourceFound(NoResourceFoundException e) {
        log.warn("[NoResourceFound] {} {}", e.getHttpMethod(), e.getResourcePath());
        return ResponseEntity
                .status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail(ErrorCode.COMMON_RESOURCE_NOT_FOUND));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<?>> handleMethodNotSupported(HttpRequestMethodNotSupportedException e) {
        log.warn("[MethodNotSupported] method={}, supported={}", e.getMethod(), e.getSupportedHttpMethods());
        return ResponseEntity
                .status(HttpStatus.METHOD_NOT_ALLOWED)
                .body(ApiResponse.fail(ErrorCode.COMMON_METHOD_NOT_ALLOWED.getCode(),
                        "지원하지 않는 HTTP 메서드입니다: " + e.getMethod()));
    }

    // ===================== 권한 거부 (@PreAuthorize 등 메서드 시큐리티) =====================

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<?>> handleAccessDenied(AccessDeniedException e) {
        log.warn("[AccessDenied] message={}", e.getMessage());
        return ResponseEntity
                .status(HttpStatus.FORBIDDEN)
                .body(ApiResponse.fail(ErrorCode.COMMON_FORBIDDEN));
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
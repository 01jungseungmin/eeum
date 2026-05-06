package com.eeum.eeum.common.response;

import com.eeum.eeum.exception.ErrorCode;
import com.fasterxml.jackson.annotation.JsonInclude; //JSON으로 변환할 때 null인 필드를 제외
import io.swagger.v3.oas.annotations.media.Schema; //Swagger 문서에 설명
import lombok.Builder; //빌더 패턴 코드 자동 생성
import lombok.Getter; //모든 필드의 getter 자동 생성

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "공통 API 응답")
public class ApiResponse<T> { //응답 데이터 타입을 상황에 따라 변경

    @Schema(description = "성공 여부", example = "true") //요청 성공 여부
    private final boolean success;

    @Schema(description = "응답 데이터. 데이터가 없는 성공 응답에서는 포함되지 않습니다.") //로그인 성공이면 토큰 정보
    private final T data;

    @Schema(description = "응답 메시지", example = "요청이 성공했습니다") //응답에 대한 간단한 메시지
    private final String message;

    @Schema(description = "에러 정보. 실패 응답에서만 포함됩니다.") //실패했을 때만 들어가는 에러 상세 정보
    private final ErrorDetail error;

    @Schema(description = "응답 시각", example = "2026-05-06T12:00:00") //응답이 생성된 시각
    private final LocalDateTime timestamp;

    @Schema(description = "요청 추적 ID", example = "7f8e9d2a-1234-5678-abcd-ef0123456789") //요청 추적용 ID
    private final String traceId;

    // ===================== 성공 응답 =====================

    public static <T> ApiResponse<T> success(T data) { //데이터가 있는 성공 응답을 만드는 정적 메서드
        return ApiResponse.<T>builder() //ApiResponse<T> 타입의 Builder를 시작
                .success(true) //성공 여부를 true
                .data(data) //응답 데이터
                .message("요청이 성공했습니다") //기본 성공 메시지
                .timestamp(LocalDateTime.now()) //현재 시각을 응답 시각
                .traceId(UUID.randomUUID().toString()) //랜덤 UUID를 traceId
                .build(); //ApiResponse 객체 생성
    }

    public static <T> ApiResponse<T> success(T data, String message) { //데이터와 메시지를 직접 지정하는 성공 응답 메서드
        return ApiResponse.<T>builder() //응답 데이터가 없는 성공 응답
                .success(true)
                .data(data)
                .message(message)
                .timestamp(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    public static ApiResponse<Void> success() { //응답 데이터가 없는 성공 응답
        return ApiResponse.<Void>builder()
                .success(true)
                .message("요청이 성공했습니다")
                .timestamp(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    // ===================== 실패 응답 =====================

    public static <T> ApiResponse<T> fail(ErrorCode errorCode) { //ErrorCode enum을 받아 실패 응답을 만드는 메서드
        return ApiResponse.<T>builder()
                .success(false)
                .message("요청 처리에 실패했습니다")
                .error(ErrorDetail.of(errorCode))
                .timestamp(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();
    }

    public static <T> ApiResponse<T> fail(String code, String message) { //ErrorCode enum이 없을 때 문자열 코드와 메시지로 실패 응답을 만드는 메서드
        return ApiResponse.<T>builder()
                .success(false)
                .message("요청 처리에 실패했습니다")
                .error(ErrorDetail.of(code, message))
                .timestamp(LocalDateTime.now())
                .traceId(UUID.randomUUID().toString())
                .build();
    }
}
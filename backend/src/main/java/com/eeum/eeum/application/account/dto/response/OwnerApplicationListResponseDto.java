package com.eeum.eeum.application.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "관리자 사장 신청 목록 응답")
@AllArgsConstructor
public class OwnerApplicationListResponseDto {

    @Schema(description = "사업자 정보 ID", example = "1")
    private Long ownerInfoId;

    @Schema(description = "회원 ID", example = "1")
    private Long accountId;

    @Schema(description = "이메일", example = "owner@test.com")
    private String email;

    @Schema(description = "신청자 이름", example = "홍길동")
    private String ownerName;

    @Schema(description = "연락처", example = "010-1234-5678")
    private String phone;

    @Schema(description = "사업자번호", example = "1234567890")
    private String businessNumber;

    @Schema(description = "개업일자", example = "2024-05-01")
    private LocalDate openingDate;

    @Schema(description = "승인 상태", example = "PENDING")
    private String approvalStatus;

    @Schema(description = "심사 요청 일시", example = "2026-05-25T12:00:00")
    private LocalDateTime reviewRequestedAt;

    @Schema(description = "상점 ID", example = "3")
    private Long storeId;

    @Schema(description = "상점명", example = "맛있는 반찬가게")
    private String storeName;

    @Schema(description = "상점 주소", example = "서울 마포구 서교동 123-4")
    private String storeAddress;

    @Schema(description = "상점 상태", example = "TEMP_CLOSED")
    private String storeStatus;

    @Schema(description = "등록일시", example = "2026-05-06T12:00:00")
    private LocalDateTime createdAt;
}
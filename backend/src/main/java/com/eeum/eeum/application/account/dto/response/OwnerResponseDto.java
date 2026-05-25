package com.eeum.eeum.application.account.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Builder
@Schema(description = "관리자 사장 신청 상세 응답")
public class OwnerResponseDto {

    @Schema(description = "사업자 정보 ID", example = "1")
    private Long ownerInfoId;

    @Schema(description = "회원 ID", example = "1")
    private Long accountId;

    @Schema(description = "사장 이름", example = "홍길동")
    private String ownerName;

    @Schema(description = "이메일", example = "owner@test.com")
    private String email;

    @Schema(description = "연락처", example = "010-1234-5678")
    private String phone;

    @Schema(description = "사업자번호", example = "1234567890")
    private String businessNumber;

    @Schema(description = "개업일자", example = "2024-05-01")
    private LocalDate openingDate;

    @Schema(description = "승인 상태", example = "PENDING")
    private String approvalStatus;

    @Schema(description = "반려 사유", example = "대표 메뉴 불일치")
    private String rejectionReason;

    @Schema(description = "심사 요청 일시", example = "2026-05-25T12:00:00")
    private LocalDateTime reviewRequestedAt;

    @Schema(description = "등록일시", example = "2026-05-06T12:00:00")
    private LocalDateTime createdAt;

    @Schema(description = "상점 ID", example = "3")
    private Long storeId;

    @Schema(description = "상점명", example = "맛있는 반찬가게")
    private String storeName;

    @Schema(description = "상점 주소", example = "서울 마포구 서교동 123-4")
    private String storeAddress;

    @Schema(description = "상점 연락처", example = "02-1234-5678")
    private String storePhone;

    @Schema(description = "상점 업종 ID", example = "1")
    private Long storeCategoryId;

    @Schema(description = "상점 업종명", example = "음식점")
    private String storeCategoryName;

    @Schema(description = "상점 설명", example = "동네 반찬을 판매하는 가게입니다.")
    private String storeDescription;

    @Schema(description = "영업시간", example = "월~토 09:00~19:00")
    private String businessHours;

    @Schema(description = "상점 상태", example = "TEMP_CLOSED")
    private String storeStatus;
}
package com.eeum.eeum.application.store.dto.response;

import com.eeum.eeum.common.dto.response.ImageResponseDto;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@Schema(description = "관리자용 상점 상세 응답")
public class AdminStoreDetailResponseDto {

    private Long storeId;
    private String name;
    private String address;
    private String phone;
    private String description;
    private List<StoreBusinessHourResponseDto> businessHours;
    private String status;
    private Double rating;
    private Integer favoriteCount;
    private Integer reviewCount;
    private Long categoryId;
    private String categoryName;
    private Long regionId;
    private String regionName;
    private Double latitude;
    private Double longitude;

    private Long ownerAccountId;
    private String ownerName;
    private String ownerEmail;
    private String ownerPhone;
    private String ownerRole;
    private String ownerStatus;

    private Long ownerInfoId;
    private String businessNumber;
    private LocalDate openingDate;
    private String approvalStatus;
    private String rejectionReason;
    private LocalDateTime reviewRequestedAt;

    private SettlementAccountResponseDto settlementAccount;
    private List<ImageResponseDto> images;
    private List<StoreNoticeResponseDto> notices;

    private LocalDateTime createdAt;
    private LocalDateTime modifiedAt;
}

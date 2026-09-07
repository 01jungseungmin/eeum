package com.eeum.eeum.application.store.mapper;

import com.eeum.eeum.application.file.FileStorageService;
import com.eeum.eeum.application.store.dto.response.SettlementAccountResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreBusinessHourResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreListResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreNoticeResponseDto;
import com.eeum.eeum.common.dto.response.ImageResponseDto;
import com.eeum.eeum.common.util.MaskingUtil;
import com.eeum.eeum.domain.store.entity.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class StoreMapper {

    private final FileStorageService fileStorageService;

    public StoreListResponseDto toStoreListResponseDto(Store store) {
        return StoreListResponseDto.builder()
                .storeId(store.getStoreId())
                .name(store.getName())
                .address(store.getAddress())
                .phone(store.getPhone())
                .description(store.getDescription())
                .status(store.getStatus().name())
                .rating(store.getRating())
                .favoriteCount(store.getFavoriteCount())
                .reviewCount(store.getReviewCount())
                .categoryId(store.getCategory() != null
                        ? store.getCategory().getCategoryId() : null)
                .categoryName(store.getCategory() != null
                        ? store.getCategory().getName() : null)
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .build();
    }

    public SettlementAccountResponseDto toSettlementDto(SettlementAccount settlementAccount) {
        if (settlementAccount == null) {
            return null;
        }

        return SettlementAccountResponseDto.builder()
                .settlementAccountId(settlementAccount.getSettlementAccountId())
                .bankName(settlementAccount.getBankName())
                .accountNumber(MaskingUtil.maskAccountNumber(settlementAccount.getAccountNumber()))
                .accountHolder(settlementAccount.getAccountHolder())
                .build();
    }

    public ImageResponseDto toImageDto(StoreImage image) {
        return ImageResponseDto.builder()
                .imageId(image.getStoreImageId())
                .imageUrl(fileStorageService.resolveImageUrl(image.getImageUrl()))
                .displayOrder(image.getDisplayOrder())
                .isThumbnail(image.isThumbnail())
                .build();
    }

    public StoreNoticeResponseDto toNoticeDto(StoreNotice notice) {
        return StoreNoticeResponseDto.builder()
                .noticeId(notice.getNoticeId())
                .title(notice.getTitle())
                .content(notice.getContent())
                .noticeType(notice.getNoticeType())
                .pinned(notice.isPinned())
                .createdAt(notice.getCreatedAt())
                .modifiedAt(notice.getModifiedAt())
                .build();
    }

    public StoreBusinessHourResponseDto toBusinessHourDto(StoreBusinessHour businessHour) {
        return StoreBusinessHourResponseDto.builder()
                .dayOfWeek(businessHour.getDayOfWeek())
                .dayLabel(businessHour.getDayOfWeek().getLabel())
                .closed(businessHour.isClosed())
                .openTime(businessHour.getOpenTime())
                .closeTime(businessHour.getCloseTime())
                .build();
    }

    public ImageResponseDto toImageResponseDto(StoreImage image) {
        return ImageResponseDto.builder()
                .imageId(image.getStoreImageId())
                .imageUrl(fileStorageService.resolveImageUrl(image.getImageUrl()))
                .displayOrder(image.getDisplayOrder())
                .isThumbnail(image.isThumbnail())
                .build();
    }
}

package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.store.dto.request.StoreBusinessHourUpdateRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreNoticeRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreStatusUpdateRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreUpdateRequestDto;
import com.eeum.eeum.application.store.dto.response.StoreBusinessHourResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreDashboardResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreNoticeResponseDto;
import com.eeum.eeum.application.store.dto.response.StoreResponseDto;
import com.eeum.eeum.application.store.mapper.StoreMapper;
import com.eeum.eeum.domain.category.entity.Category;
import com.eeum.eeum.domain.category.enums.CategoryType;
import com.eeum.eeum.domain.category.repository.CategoryRepository;
import com.eeum.eeum.domain.chat.entity.ChatRoom;
import com.eeum.eeum.domain.chat.enums.ChatRoomRefType;
import com.eeum.eeum.domain.chat.enums.ChatRoomType;
import com.eeum.eeum.domain.chat.repository.ChatRoomRepository;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.product.enums.ProductStatus;
import com.eeum.eeum.domain.product.repository.ProductRepository;
import com.eeum.eeum.domain.reservation.enums.VisitReservationStatus;
import com.eeum.eeum.domain.reservation.repository.VisitReservationRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreBusinessHour;
import com.eeum.eeum.domain.store.entity.StoreImage;
import com.eeum.eeum.domain.store.entity.StoreNotice;
import com.eeum.eeum.domain.store.enums.StoreDayOfWeek;
import com.eeum.eeum.domain.store.repository.StoreBusinessHourRepository;
import com.eeum.eeum.domain.store.repository.StoreImageRepository;
import com.eeum.eeum.domain.store.repository.StoreNoticeRepository;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreService {

    private final StoreRepository storeRepository;
    private final StoreNoticeRepository storeNoticeRepository;
    private final CategoryRepository categoryRepository;
    private final StoreBusinessHourRepository storeBusinessHourRepository;
    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final StoreImageRepository storeImageRepository;
    private final VisitReservationRepository visitReservationRepository;
    private final ChatRoomRepository chatRoomRepository;
    private final StoreMapper storeMapper;

    // ===================== 상점 조회/수정 =====================

    @Transactional(readOnly = true)
    public StoreResponseDto getMyStore(Long accountId) {
        Store store = getStore(accountId);
        return toDto(store);
    }

    @Transactional
    public StoreResponseDto updateStore(Long accountId, StoreUpdateRequestDto request) {
        Store store = getStore(accountId);

        Category category = categoryRepository
                .findByCategoryIdAndTypeAndIsActiveTrue(request.getCategoryId(), CategoryType.STORE)
                .orElseThrow(() -> new BusinessException(ErrorCode.CATEGORY_NOT_FOUND));

        store.updateCategory(category);
        store.updateBasicInfo(
                request.getName(),
                request.getAddress(),
                request.getPhone(),
                request.getDescription()
        );

        log.info("상점 정보 수정: accountId={}", accountId);
        return toDto(store);
    }

    @Transactional
    public void updateStoreStatus(Long accountId, StoreStatusUpdateRequestDto request) {
        Store store = getStore(accountId);

        switch (request.getStatus()) {
            case OPEN -> store.reopen();
            case TEMP_CLOSED -> store.tempClose();
            case CLOSED -> store.close();
            default -> throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        log.info("상점 상태 변경: accountId={}, status={}", accountId, request.getStatus());
    }

    // ===================== 대시보드 메서드 =====================
    @Transactional(readOnly = true)
    public StoreDashboardResponseDto getDashboard(Long accountId) {
        Store store = storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));

        LocalDateTime todayStart = LocalDate.now().atStartOfDay();
        LocalDateTime monthStart = LocalDate.now().withDayOfMonth(1).atStartOfDay();
        LocalDateTime now = LocalDateTime.now();

        String thumbnailUrl = storeImageRepository
                .findByStore_StoreIdAndIsThumbnailTrue(store.getStoreId())
                .map(StoreImage::getImageUrl)
                .orElse(null);

        // 매출은 결제 완료 후 전이된 상태까지 포함해야 실제 매출과 일치한다 (PAID만 합산하면 사장이
        // 주문을 확인/준비완료 처리하는 순간 매출에서 누락됨).
        List<OrderStatus> revenueStatuses = List.of(
                OrderStatus.PAID, OrderStatus.CONFIRMED, OrderStatus.READY, OrderStatus.COMPLETED);

        long todayOrderCount = orderRepository
                .countByStore_StoreIdAndCreatedAtBetween(store.getStoreId(), todayStart, now);

        BigDecimal todayRevenue = orderRepository
                .sumTotalPriceByStoreAndCreatedAtBetween(store.getStoreId(), todayStart, now, revenueStatuses);

        long monthOrderCount = orderRepository
                .countByStore_StoreIdAndCreatedAtBetween(store.getStoreId(), monthStart, now);

        BigDecimal monthRevenue = orderRepository
                .sumTotalPriceByStoreAndCreatedAtBetween(store.getStoreId(), monthStart, now, revenueStatuses);

        long pendingOrderCount = orderRepository
                .countByStore_StoreIdAndStatus(store.getStoreId(), OrderStatus.PENDING);

        long pendingReservationCount = visitReservationRepository
                .countByStore_StoreIdAndStatus(store.getStoreId(), VisitReservationStatus.PENDING);

        long totalProductCount = productRepository
                .countByStore_StoreId(store.getStoreId());

        long soldOutProductCount = productRepository
                .countByStore_StoreIdAndStatus(store.getStoreId(), ProductStatus.SOLD_OUT);

        // (STORE, storeId) 조합은 유니크가 아니라 GROUP/GROUP_STREET 방이 공존할 수 있다 —
        // 단건 Optional 조회는 2건 이상일 때 예외로 대시보드 전체가 실패하므로 목록 조회 후
        // 활성 방 중 상점 단톡방(GROUP)을 우선 선택한다.
        List<ChatRoom> storeRooms = chatRoomRepository
                .findAllByRefTypeAndRefId(ChatRoomRefType.STORE, store.getStoreId());
        ChatRoom storeChatRoom = storeRooms.stream()
                .filter(ChatRoom::isActive)
                .filter(room -> room.getType() == ChatRoomType.GROUP)
                .findFirst()
                .orElseGet(() -> storeRooms.stream()
                        .filter(ChatRoom::isActive)
                        .findFirst()
                        .orElse(null));

        return toDashboardDto(
                store,
                thumbnailUrl,
                todayOrderCount,
                todayRevenue,
                monthOrderCount,
                monthRevenue,
                pendingOrderCount,
                pendingReservationCount,
                totalProductCount,
                soldOutProductCount,
                storeChatRoom
        );
    }
    // ===================== 영업시간 관리 =====================

    @Transactional(readOnly = true)
    public List<StoreBusinessHourResponseDto> getBusinessHours(Long accountId) {
        Store store = getStore(accountId);

        return storeBusinessHourRepository.findByStore_StoreId(store.getStoreId())
                .stream()
                .sorted(Comparator.comparingInt(hour -> hour.getDayOfWeek().getOrder()))
                .map(this::toBusinessHourDto)
                .toList();
    }

    @Transactional
    public void updateBusinessHours(Long accountId, StoreBusinessHourUpdateRequestDto request) {
        Store store = getStore(accountId);

        validateBusinessHoursRequest(request);

        Map<StoreDayOfWeek, StoreBusinessHour> existingMap =
                storeBusinessHourRepository.findByStore_StoreId(store.getStoreId())
                        .stream()
                        .collect(Collectors.toMap(
                                StoreBusinessHour::getDayOfWeek,
                                Function.identity()
                        ));

        for (StoreBusinessHourUpdateRequestDto.BusinessHourItem item : request.getBusinessHours()) {
            StoreBusinessHour businessHour = existingMap.get(item.getDayOfWeek());

            if (businessHour == null) {
                StoreBusinessHour newBusinessHour = StoreBusinessHour.create(
                        store,
                        item.getDayOfWeek(),
                        item.isClosed(),
                        item.getOpenTime(),
                        item.getCloseTime()
                );
                storeBusinessHourRepository.save(newBusinessHour);
                continue;
            }

            businessHour.update(
                    item.isClosed(),
                    item.getOpenTime(),
                    item.getCloseTime()
            );
        }

        log.info("상점 영업시간 수정: accountId={}, storeId={}", accountId, store.getStoreId());
    }

    // ===================== 공지 관리 =====================

    @Transactional(readOnly = true)
    public List<StoreNoticeResponseDto> getNotices(Long accountId) {
        Store store = getStore(accountId);
        return storeNoticeRepository
                .findByStore_StoreIdAndIsActiveTrueOrderByIsPinnedDescCreatedAtDesc(store.getStoreId())
                .stream()
                .map(this::toNoticeDto)
                .toList();
    }

    @Transactional
    public StoreNoticeResponseDto createNotice(Long accountId, StoreNoticeRequestDto request) {
        Store store = getStore(accountId);
        StoreNotice notice = StoreNotice.create(
                store,
                request.getTitle(),
                request.getContent(),
                request.getNoticeType(),
                request.isPinned()
        );
        storeNoticeRepository.save(notice);
        log.info("공지 등록: storeId={}", store.getStoreId());
        return toNoticeDto(notice);
    }

    @Transactional
    public StoreNoticeResponseDto updateNotice(Long accountId, Long noticeId,
            StoreNoticeRequestDto request) {
        StoreNotice notice = getNoticeWithOwnerCheck(accountId, noticeId);
        notice.update(
                request.getTitle(),
                request.getContent(),
                request.getNoticeType(),
                request.isPinned()
        );
        return toNoticeDto(notice);
    }

    @Transactional
    public void deleteNotice(Long accountId, Long noticeId) {
        StoreNotice notice = getNoticeWithOwnerCheck(accountId, noticeId);
        notice.deactivate();
    }

    // ===================== 유틸 =====================

    // 사장 accountId로 내 상점 ID를 조회
    public Long getOwnerStoreId(Long accountId) {
        return getStore(accountId).getStoreId();
    }

    private Store getStore(Long accountId) {
        return storeRepository.findByAccount_AccountId(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private StoreNotice getNoticeWithOwnerCheck(Long accountId, Long noticeId) {
        Store store = getStore(accountId);
        StoreNotice notice = storeNoticeRepository.findById(noticeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOTICE_NOT_FOUND));
        if (!notice.getStore().getStoreId().equals(store.getStoreId())) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }
        return notice;
    }

    private void validateBusinessHoursRequest(StoreBusinessHourUpdateRequestDto request) {
        if (request.getBusinessHours() == null || request.getBusinessHours().isEmpty()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        long distinctDayCount = request.getBusinessHours().stream()
                .map(StoreBusinessHourUpdateRequestDto.BusinessHourItem::getDayOfWeek)
                .distinct()
                .count();

        if (distinctDayCount != request.getBusinessHours().size()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        if (request.getBusinessHours().size() != StoreDayOfWeek.values().length) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        for (StoreBusinessHourUpdateRequestDto.BusinessHourItem item : request.getBusinessHours()) {
            validateBusinessHourItem(item);
        }
    }

    private void validateBusinessHourItem(StoreBusinessHourUpdateRequestDto.BusinessHourItem item) {
        if (item.getDayOfWeek() == null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        if (item.isClosed()) {
            return;
        }

        if (item.getOpenTime() == null || item.getCloseTime() == null) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        if (!item.getOpenTime().isBefore(item.getCloseTime())) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }
    }

    private StoreBusinessHourResponseDto toBusinessHourDto(StoreBusinessHour businessHour) {
        return StoreBusinessHourResponseDto.builder()
                .dayOfWeek(businessHour.getDayOfWeek())
                .dayLabel(businessHour.getDayOfWeek().getLabel())
                .closed(businessHour.isClosed())
                .openTime(businessHour.getOpenTime())
                .closeTime(businessHour.getCloseTime())
                .build();
    }

    private StoreResponseDto toDto(Store store) {
        return StoreResponseDto.builder()
                .storeId(store.getStoreId())
                .name(store.getName())
                .address(store.getAddress())
                .phone(store.getPhone())
                .description(store.getDescription())
                .businessHours(getBusinessHourDtos(store.getStoreId()))
                .status(store.getStatus().name())
                .rating(store.getRating())
                .favoriteCount(store.getFavoriteCount())
                .reviewCount(store.getReviewCount())
                .categoryId(store.getCategory() != null ? store.getCategory().getCategoryId() : null)
                .categoryName(store.getCategory() != null ? store.getCategory().getName() : null)
                .latitude(store.getLatitude())
                .longitude(store.getLongitude())
                .createdAt(store.getCreatedAt())
                .build();
    }

    private StoreNoticeResponseDto toNoticeDto(StoreNotice n) {
        return StoreNoticeResponseDto.builder()
                .noticeId(n.getNoticeId())
                .title(n.getTitle())
                .content(n.getContent())
                .pinned(n.isPinned())
                .noticeType(n.getNoticeType())
                .createdAt(n.getCreatedAt())
                .modifiedAt(n.getModifiedAt())
                .build();
    }

    private StoreDashboardResponseDto toDashboardDto(
            Store store,
            String thumbnailUrl,
            long todayOrderCount,
            BigDecimal todayRevenue,
            long monthOrderCount,
            BigDecimal monthRevenue,
            long pendingOrderCount,
            long pendingReservationCount,
            long totalProductCount,
            long soldOutProductCount,
            ChatRoom storeChatRoom
    ) {
        return StoreDashboardResponseDto.builder()
                .storeId(store.getStoreId())
                .storeName(store.getName())
                .storeStatus(store.getStatus())
                .thumbnailUrl(thumbnailUrl)
                .todayOrderCount(todayOrderCount)
                .todayRevenue(todayRevenue != null ? todayRevenue : BigDecimal.ZERO)
                .monthOrderCount(monthOrderCount)
                .monthRevenue(monthRevenue != null ? monthRevenue : BigDecimal.ZERO)
                .pendingOrderCount(pendingOrderCount)
                .pendingReservationCount(pendingReservationCount)
                .unansweredReviewCount(0L)
                .totalProductCount(totalProductCount)
                .soldOutProductCount(soldOutProductCount)
                .averageRating(store.getRating() != null ? store.getRating() : 0.0)
                .totalReviewCount(store.getReviewCount() != null ? store.getReviewCount() : 0)
                .storeChatRoomCreated(storeChatRoom != null)
                .storeChatRoomId(storeChatRoom != null ? storeChatRoom.getChatroomId() : null)
                .build();
    }

    private List<StoreBusinessHourResponseDto> getBusinessHourDtos(Long storeId) {
        return storeBusinessHourRepository.findByStore_StoreId(storeId)
                .stream()
                .sorted(Comparator.comparingInt(hour -> hour.getDayOfWeek().getOrder()))
                .map(this::toBusinessHourDto)
                .toList();
    }
}
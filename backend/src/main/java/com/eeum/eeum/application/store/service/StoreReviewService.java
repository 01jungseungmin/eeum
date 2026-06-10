package com.eeum.eeum.application.store.service;

import com.eeum.eeum.application.store.dto.request.StoreReviewCreateRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreReviewReplyRequestDto;
import com.eeum.eeum.application.store.dto.request.StoreReviewUpdateRequestDto;
import com.eeum.eeum.application.store.dto.response.*;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.account.repository.AccountRepository;
import com.eeum.eeum.domain.order.entity.Order;
import com.eeum.eeum.domain.order.enums.OrderStatus;
import com.eeum.eeum.domain.order.repository.OrderRepository;
import com.eeum.eeum.domain.store.entity.Store;
import com.eeum.eeum.domain.store.entity.StoreReview;
import com.eeum.eeum.domain.store.entity.StoreReviewImage;
import com.eeum.eeum.domain.store.entity.StoreReviewReply;
import com.eeum.eeum.domain.store.event.StoreReviewCreatedEvent;
import com.eeum.eeum.domain.store.event.StoreReviewReplyCreatedEvent;
import com.eeum.eeum.domain.store.repository.StoreRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewImageRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewReplyRepository;
import com.eeum.eeum.domain.store.repository.StoreReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class StoreReviewService {

    private static final int MAX_REVIEW_IMAGE_COUNT = 10;

    private final StoreRepository storeRepository;
    private final StoreReviewRepository storeReviewRepository;
    private final StoreReviewImageRepository storeReviewImageRepository;
    private final StoreReviewReplyRepository storeReviewReplyRepository;
    private final OrderRepository orderRepository;
    private final AccountRepository accountRepository;
    private final ApplicationEventPublisher eventPublisher;

    // ===================== 공개 조회 =====================

    //상점 리뷰 목록 조회 (비회원 포함)
    @Transactional(readOnly = true)
    public Page<StoreReviewResponseDto> getReviews(Long storeId, Pageable pageable) {
        getStoreOrThrow(storeId);
        Page<StoreReview> reviews = storeReviewRepository
                .findByStore_StoreIdOrderByCreatedAtDesc(storeId, pageable);
        return reviews.map(this::toResponseDto);
    }

    // 상점 리뷰 단건 상세 조회 (비회원 포함)
    @Transactional(readOnly = true)
    public StoreReviewDetailResponseDto getReviewDetail(Long storeId, Long reviewId) {
        StoreReview review = getReviewOrThrow(storeId, reviewId);
        List<StoreReviewImage> images = storeReviewImageRepository
                .findByStoreReview_StorereviewIdOrderByDisplayOrderAsc(reviewId);
        StoreReviewReplyResponseDto replyDto = buildReplyDto(reviewId);
        return StoreReviewDetailResponseDto.of(review, images, replyDto);
    }

    // ===================== 리뷰 작성/수정/삭제 (일반 회원) =====================

    // 리뷰 작성 — 거래 완료(COMPLETED) 주문만 허용, 1주문 1리뷰
    @Transactional
    public StoreReviewResponseDto createReview(
            Long accountId,
            Long storeId,
            StoreReviewCreateRequestDto request
    ) {
        Store store = getStoreOrThrow(storeId);
        Account account = getAccountOrThrow(accountId);

        // 주문 검증 — 해당 계정의 완료된 주문이어야 함
        Order order = orderRepository
                .findByOrderIdAndAccount_AccountId(request.getOrderId(), accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ORDER_NOT_FOUND));

        if (!order.getStore().getStoreId().equals(storeId)) {
            throw new BusinessException(ErrorCode.STORE_REVIEW_ORDER_REQUIRED);
        }

        if (order.getStatus() != OrderStatus.COMPLETED) {
            throw new BusinessException(ErrorCode.STORE_REVIEW_ORDER_REQUIRED);
        }

        // 해당 주문에 이미 리뷰가 있으면 중복 작성 방지
        if (storeReviewRepository.existsByOrder_OrderId(request.getOrderId())) {
            throw new BusinessException(ErrorCode.STORE_REVIEW_ALREADY_EXISTS);
        }

        // 리뷰 저장
        StoreReview review = StoreReview.create(store, account, order,
                request.getRating(), request.getContent());

        try {
            storeReviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.STORE_REVIEW_ALREADY_EXISTS);
        }

        // 이미지 저장 (신규 리뷰이므로 currentCount = 0)
        if (request.getImageUrls() != null && !request.getImageUrls().isEmpty()) {
            saveReviewImages(review, request.getImageUrls(), 0);
        }

        // Store 평점 및 리뷰 수 갱신
        recalculateStoreRating(store);

        log.info("상점 리뷰 작성: storeId={}, accountId={}, reviewId={}",
                storeId, accountId, review.getStorereviewId());

        eventPublisher.publishEvent(new StoreReviewCreatedEvent(
                store.getAccount().getAccountId(),
                account.getName(),
                store.getName(),
                storeId,
                review.getStorereviewId()));

        List<StoreReviewImage> savedImages = storeReviewImageRepository
                .findByStoreReview_StorereviewIdOrderByDisplayOrderAsc(review.getStorereviewId());
        return StoreReviewResponseDto.of(review, savedImages, null);
    }

    // 리뷰 수정 — 본인만 가능
    @Transactional
    public StoreReviewResponseDto updateReview(
            Long accountId,
            Long storeId,
            Long reviewId,
            StoreReviewUpdateRequestDto request
    ) {
        StoreReview review = getReviewOrThrow(storeId, reviewId);
        checkReviewOwnership(review, accountId);

        int oldRating = review.getRating();
        review.update(request.getRating(), request.getContent());

        // 평점이 바뀐 경우에만 Store 평점 재계산
        if (oldRating != request.getRating()) {
            recalculateStoreRating(review.getStore());
        }

        log.info("상점 리뷰 수정: reviewId={}, accountId={}", reviewId, accountId);

        List<StoreReviewImage> images = storeReviewImageRepository
                .findByStoreReview_StorereviewIdOrderByDisplayOrderAsc(reviewId);
        StoreReviewReplyResponseDto replyDto = buildReplyDto(reviewId);
        return StoreReviewResponseDto.of(review, images, replyDto);
    }

    // 리뷰 삭제 — 본인만 가능
    @Transactional
    public void deleteReview(Long accountId, Long storeId, Long reviewId) {
        StoreReview review = getReviewOrThrow(storeId, reviewId);
        checkReviewOwnership(review, accountId);

        Store store = review.getStore();

        // 연관 이미지 / 답글 먼저 삭제
        storeReviewImageRepository.deleteAllByStoreReview_StorereviewId(reviewId);
        storeReviewReplyRepository.deleteByStoreReview_StorereviewId(reviewId);
        storeReviewRepository.delete(review);

        // Store 평점 재계산
        recalculateStoreRating(store);

        log.info("상점 리뷰 삭제: reviewId={}, accountId={}", reviewId, accountId);
    }

    // ===================== 리뷰 이미지 관리 =====================

    // 리뷰 이미지 추가
    @Transactional
    public StoreReviewResponseDto addReviewImages(
            Long accountId,
            Long storeId,
            Long reviewId,
            List<String> imageUrls
    ) {
        StoreReview review = getReviewOrThrow(storeId, reviewId);
        checkReviewOwnership(review, accountId);

        if (imageUrls == null || imageUrls.isEmpty()) {
            throw new BusinessException(ErrorCode.COMMON_INVALID_PARAMETER);
        }

        // 총 이미지 수 검증 — count 쿼리 결과를 saveReviewImages에 재사용해 중복 조회 방지
        int currentCount = storeReviewImageRepository
                .countByStoreReview_StorereviewId(reviewId);

        if (currentCount + imageUrls.size() > MAX_REVIEW_IMAGE_COUNT) {
            throw new BusinessException(ErrorCode.IMAGE_LIMIT_EXCEEDED);
        }

        saveReviewImages(review, imageUrls, currentCount);

        log.info("리뷰 이미지 추가: reviewId={}, count={}", reviewId, imageUrls.size());

        List<StoreReviewImage> images = storeReviewImageRepository
                .findByStoreReview_StorereviewIdOrderByDisplayOrderAsc(reviewId);
        StoreReviewReplyResponseDto replyDto = buildReplyDto(reviewId);
        return StoreReviewResponseDto.of(review, images, replyDto);
    }

    // 리뷰 이미지 삭제
    @Transactional
    public void deleteReviewImage(Long accountId, Long storeId, Long reviewId, Long imageId) {
        StoreReview review = getReviewOrThrow(storeId, reviewId);
        checkReviewOwnership(review, accountId);

        StoreReviewImage image = storeReviewImageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));

        if (!image.getStoreReview().getStorereviewId().equals(reviewId)) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }

        boolean wasThumbnail = image.isThumbnail();
        storeReviewImageRepository.delete(image);

        if (wasThumbnail) {
            storeReviewImageRepository
                    .findByStoreReview_StorereviewIdOrderByDisplayOrderAsc(reviewId)
                    .stream()
                    .findFirst()
                    .ifPresent(StoreReviewImage::markAsThumbnail);
        }
        log.info("리뷰 이미지 삭제: imageId={}", imageId);
    }

    // 리뷰 대표 이미지 지정
    @Transactional
    public void setReviewImageThumbnail(
            Long accountId, Long storeId, Long reviewId, Long imageId
    ) {
        StoreReview review = getReviewOrThrow(storeId, reviewId);
        checkReviewOwnership(review, accountId);

        StoreReviewImage newThumbnail = storeReviewImageRepository.findById(imageId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IMAGE_NOT_FOUND));

        if (!newThumbnail.getStoreReview().getStorereviewId().equals(reviewId)) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }

        storeReviewImageRepository
                .findByStoreReview_StorereviewIdOrderByDisplayOrderAsc(reviewId)
                .forEach(StoreReviewImage::unmarkAsThumbnail);
        newThumbnail.markAsThumbnail();

        log.info("리뷰 대표 이미지 변경: reviewId={}, imageId={}", reviewId, imageId);
    }

    // ===================== 사장 답글 (ROLE_OWNER) =====================

    // 답글 작성 — 해당 상점의 사장만 가능, 1리뷰 1답글
    @Transactional
    public StoreReviewReplyResponseDto createReply(
            Long accountId,
            Long storeId,
            Long reviewId,
            StoreReviewReplyRequestDto request
    ) {
        checkStoreOwnership(storeId, accountId);
        StoreReview review = getReviewOrThrow(storeId, reviewId);

        if (storeReviewReplyRepository.existsByStoreReview_StorereviewId(reviewId)) {
            throw new BusinessException(ErrorCode.STORE_REVIEW_REPLY_ALREADY_EXISTS);
        }

        Account account = getAccountOrThrow(accountId);
        StoreReviewReply reply = StoreReviewReply.create(review, account, request.getContent());

        try {
            storeReviewReplyRepository.saveAndFlush(reply);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.STORE_REVIEW_REPLY_ALREADY_EXISTS);
        }

        log.info("리뷰 답글 작성: reviewId={}, accountId={}", reviewId, accountId);

        eventPublisher.publishEvent(new StoreReviewReplyCreatedEvent(
                review.getAccount().getAccountId(),
                review.getStore().getName(),
                storeId,
                reviewId));

        return StoreReviewReplyResponseDto.from(reply);
    }

    // 답글 수정 — 해당 상점의 사장만 가능
    @Transactional
    public StoreReviewReplyResponseDto updateReply(
            Long accountId,
            Long storeId,
            Long reviewId,
            StoreReviewReplyRequestDto request
    ) {
        checkStoreOwnership(storeId, accountId);
        getReviewOrThrow(storeId, reviewId); // 리뷰 존재 확인

        StoreReviewReply reply = storeReviewReplyRepository
                .findByStoreReview_StorereviewId(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND));

        reply.update(request.getContent());

        log.info("리뷰 답글 수정: reviewId={}, accountId={}", reviewId, accountId);
        return StoreReviewReplyResponseDto.from(reply);
    }

    // 답글 삭제 — 해당 상점의 사장만 가능
    @Transactional
    public void deleteReply(Long accountId, Long storeId, Long reviewId) {
        checkStoreOwnership(storeId, accountId);
        getReviewOrThrow(storeId, reviewId); // 리뷰 존재 확인

        StoreReviewReply reply = storeReviewReplyRepository
                .findByStoreReview_StorereviewId(reviewId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMON_RESOURCE_NOT_FOUND));

        storeReviewReplyRepository.delete(reply);
        log.info("리뷰 답글 삭제: reviewId={}, accountId={}", reviewId, accountId);
    }

    // ===================== 사장용 조회 (ROLE_OWNER) =====================

    //사장용 리뷰 목록 조회
    @Transactional(readOnly = true)
    public Page<OwnerStoreReviewResponseDto> getReviewsForOwner(
            Long accountId, Long storeId, Pageable pageable
    ) {
        checkStoreOwnership(storeId, accountId);
        Page<StoreReview> reviews = storeReviewRepository
                .findByStore_StoreIdOrderByCreatedAtDesc(storeId, pageable);
        return reviews.map(review -> {
            int imageCount = storeReviewImageRepository
                    .countByStoreReview_StorereviewId(review.getStorereviewId());
            boolean hasReply = storeReviewReplyRepository
                    .existsByStoreReview_StorereviewId(review.getStorereviewId());
            return OwnerStoreReviewResponseDto.of(review, imageCount, hasReply);
        });
    }

    //사장용 리뷰 상세 조회
    @Transactional(readOnly = true)
    public OwnerStoreReviewDetailResponseDto getReviewDetailForOwner(
            Long accountId, Long storeId, Long reviewId
    ) {
        checkStoreOwnership(storeId, accountId);
        StoreReview review = getReviewOrThrow(storeId, reviewId);
        List<StoreReviewImage> images = storeReviewImageRepository
                .findByStoreReview_StorereviewIdOrderByDisplayOrderAsc(reviewId);
        StoreReviewReplyResponseDto replyDto = buildReplyDto(reviewId);
        return OwnerStoreReviewDetailResponseDto.of(review, images, replyDto);
    }

    // ===================== 내부 헬퍼 =====================

    // 평균 평점 재계산 후 Store 엔티티에 반영(전체 리뷰를 조회하는 대신 집계 쿼리를 사용)
    private void recalculateStoreRating(Store store) {
        double avg = storeReviewRepository.calculateAverageRating(store.getStoreId());
        int count = Math.toIntExact(storeReviewRepository.countByStoreId(store.getStoreId()));

        store.updateRating(avg, count);
        log.debug("Store 평점 갱신: storeId={}, avg={}, count={}", store.getStoreId(), avg, count);
    }

    // 리뷰 이미지 목록을 저장
    // @param currentCount 이미 저장된 이미지 수 (호출 측에서 조회한 값을 재사용해 중복 쿼리 방지) - 신규 리뷰 생성 시에는 0을 전달
    private void saveReviewImages(StoreReview review, List<String> imageUrls, int currentCount) {
        boolean hasExistingThumbnail = storeReviewImageRepository
                .existsByStoreReview_StorereviewIdAndIsThumbnailTrue(review.getStorereviewId());

        List<StoreReviewImage> images = new java.util.ArrayList<>();

        for (int i = 0; i < imageUrls.size(); i++) {
            boolean isThumbnail = (!hasExistingThumbnail && currentCount == 0 && i == 0);

            images.add(StoreReviewImage.create(
                    review,
                    imageUrls.get(i),
                    currentCount + i + 1,
                    isThumbnail
            ));
        }

        storeReviewImageRepository.saveAll(images);
    }

    private StoreReviewReplyResponseDto buildReplyDto(Long reviewId) {
        return storeReviewReplyRepository
                .findByStoreReview_StorereviewId(reviewId)
                .map(StoreReviewReplyResponseDto::from)
                .orElse(null);
    }

    private void checkReviewOwnership(StoreReview review, Long accountId) {
        if (!review.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.STORE_REVIEW_ACCESS_DENIED);
        }
    }

    private void checkStoreOwnership(Long storeId, Long accountId) {
        Store store = storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
        if (!store.isOwnedBy(accountId)) {
            throw new BusinessException(ErrorCode.STORE_ACCESS_DENIED);
        }
    }

    private StoreReviewResponseDto toResponseDto(StoreReview review) {
        Long reviewId = review.getStorereviewId();

        List<StoreReviewImage> images = storeReviewImageRepository
                .findByStoreReview_StorereviewIdOrderByDisplayOrderAsc(reviewId);

        StoreReviewReplyResponseDto replyDto = buildReplyDto(reviewId);

        return StoreReviewResponseDto.of(review, images, replyDto);
    }

    private Store getStoreOrThrow(Long storeId) {
        return storeRepository.findById(storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_NOT_FOUND));
    }

    private StoreReview getReviewOrThrow(Long storeId, Long reviewId) {
        return storeReviewRepository
                .findByStorereviewIdAndStore_StoreId(reviewId, storeId)
                .orElseThrow(() -> new BusinessException(ErrorCode.STORE_REVIEW_NOT_FOUND));
    }

    private Account getAccountOrThrow(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> new BusinessException(ErrorCode.ACCOUNT_NOT_FOUND));
    }
}

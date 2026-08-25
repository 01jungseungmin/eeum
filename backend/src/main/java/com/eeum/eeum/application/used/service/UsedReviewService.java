package com.eeum.eeum.application.used.service;

import com.eeum.eeum.application.account.service.AccountWriteGuard;
import com.eeum.eeum.application.used.dto.request.UsedReviewCreateRequestDto;
import com.eeum.eeum.application.used.dto.request.UsedReviewUpdateRequestDto;
import com.eeum.eeum.application.used.dto.response.UsedReviewResponseDto;
import com.eeum.eeum.domain.account.entity.Account;
import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.entity.UsedReview;
import com.eeum.eeum.domain.used.repository.UsedProductRepository;
import com.eeum.eeum.domain.used.repository.UsedReviewRepository;
import com.eeum.eeum.exception.BusinessException;
import com.eeum.eeum.exception.ConflictException;
import com.eeum.eeum.exception.ErrorCode;
import com.eeum.eeum.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 중고거래 후기.
 *
 * <p>작성 자격은 <b>거래가 완료됐고(SOLD) 본인이 그 거래의 구매자로 지정된 경우</b>다.
 * 구매자 지정은 판매자가 예약·판매완료 시 하며({@code UsedProductService}), 지정이 없는 거래
 * (앱 밖에서 성사돼 상태만 정리한 글)에는 후기가 붙지 않는다.
 *
 * <p>후기는 구매자만 쓴다 — 판매자는 받기만 한다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UsedReviewService {

    private final AccountWriteGuard accountWriteGuard;
    private final UsedProductRepository usedProductRepository;
    private final UsedReviewRepository usedReviewRepository;

    /**
     * 후기 작성.
     *
     * <p>잠금 순서는 프로젝트 전역 규약대로 <b>account → used_product</b>다. 게시글 행을 잠그는
     * 이유는 자격 판정의 근거인 {@code status}·{@code buyer}가 판매자의 상태 변경과 같은 행에
     * 있어서다. 잠그지 않으면 "판매완료 확인 → 후기 저장" 사이에 예약 취소가 끼어들어
     * 구매자가 아닌 사람의 후기가 남는다.
     */
    @Transactional
    public UsedReviewResponseDto create(
            Long reviewerId,
            Long usedProductId,
            UsedReviewCreateRequestDto request
    ) {
        Account reviewer = accountWriteGuard.lockActive(reviewerId);
        UsedProduct product = getForUpdateOrThrow(usedProductId);

        // SOLD + 지정 구매자를 함께 본다. 예약 단계에서는 아직 후기를 열지 않는다.
        if (!product.isPurchasedBy(reviewerId)) {
            throw new BusinessException(ErrorCode.USED_REVIEW_NOT_COMPLETED);
        }

        if (usedReviewRepository.existsByUsedProduct_UsedProductIdAndReviewer_AccountId(
                usedProductId, reviewerId)) {
            throw new ConflictException(ErrorCode.USED_REVIEW_ALREADY_EXISTS);
        }

        UsedReview review = UsedReview.create(
                product, reviewer, request.getRating(), request.getContent());

        // 위 존재 확인과 INSERT 사이에 다른 요청이 끼어들 수 있다.
        // 유니크 위반을 같은 오류로 변환해 중복이 조용히 들어가지 않게 한다.
        try {
            usedReviewRepository.saveAndFlush(review);
        } catch (DataIntegrityViolationException e) {
            log.warn("중고거래 후기 중복 작성 차단: usedProductId={}, reviewerId={}",
                    usedProductId, reviewerId);
            throw new ConflictException(ErrorCode.USED_REVIEW_ALREADY_EXISTS);
        }

        log.info("중고거래 후기 작성: usedReviewId={}, usedProductId={}, reviewerId={}",
                review.getUsedReviewId(), usedProductId, reviewerId);
        return UsedReviewResponseDto.from(review);
    }

    @Transactional
    public UsedReviewResponseDto update(
            Long reviewerId,
            Long usedReviewId,
            UsedReviewUpdateRequestDto request
    ) {
        UsedReview review = getOwnedOrThrow(reviewerId, usedReviewId);
        review.update(request.getRating(), request.getContent());

        // modifiedAt은 flush 시점에 채워진다. 먼저 반영하지 않으면 응답에 수정 전 값이 담긴다.
        usedReviewRepository.flush();

        log.info("중고거래 후기 수정: usedReviewId={}, reviewerId={}", usedReviewId, reviewerId);
        return UsedReviewResponseDto.from(review);
    }

    @Transactional
    public void delete(Long reviewerId, Long usedReviewId) {
        UsedReview review = getOwnedOrThrow(reviewerId, usedReviewId);
        usedReviewRepository.delete(review);

        log.info("중고거래 후기 삭제: usedReviewId={}, reviewerId={}", usedReviewId, reviewerId);
    }

    // ===================== 내부 헬퍼 =====================

    // 삭제된 게시글에는 새 후기를 쓸 수 없다. 이미 쓴 후기는 게시글이 지워져도 남는다 —
    // 판매완료 글에 후기가 매달려 있다는 것이 UsedProduct를 Soft Delete로 둔 이유다.
    private UsedProduct getForUpdateOrThrow(Long usedProductId) {
        return usedProductRepository.findByUsedProductIdForUpdate(usedProductId)
                .filter(product -> !product.isDeleted())
                .orElseThrow(() -> new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND));
    }

    /**
     * 수정·삭제 대상 조회.
     *
     * <p>조회 조건에 작성자를 함께 넣는다. 후기를 먼저 읽고 소유권을 나중에 비교하면
     * 남의 후기 ID로 존재 여부를 알아낼 수 있다(404와 403이 갈린다).
     * 여기서는 남의 후기든 없는 후기든 같은 404로 응답한다.
     */
    private UsedReview getOwnedOrThrow(Long reviewerId, Long usedReviewId) {
        return usedReviewRepository
                .findByUsedReviewIdAndReviewer_AccountId(usedReviewId, reviewerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USED_REVIEW_NOT_FOUND));
    }
}

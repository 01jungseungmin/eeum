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
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
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
        return UsedReviewResponseDto.from(review, reviewerId);
    }

    @Transactional
    public UsedReviewResponseDto update(
            Long reviewerId,
            Long usedReviewId,
            UsedReviewUpdateRequestDto request
    ) {
        UsedReview review = getOwnedForUpdateOrThrow(reviewerId, usedReviewId);
        review.update(request.getRating(), request.getContent());

        // modifiedAt은 flush 시점에 채워진다. 먼저 반영하지 않으면 응답에 수정 전 값이 담긴다.
        usedReviewRepository.flush();

        log.info("중고거래 후기 수정: usedReviewId={}, reviewerId={}", usedReviewId, reviewerId);
        return UsedReviewResponseDto.from(review, reviewerId);
    }

    @Transactional
    public void delete(Long reviewerId, Long usedReviewId) {
        UsedReview review = getOwnedForUpdateOrThrow(reviewerId, usedReviewId);
        usedReviewRepository.delete(review);

        log.info("중고거래 후기 삭제: usedReviewId={}, reviewerId={}", usedReviewId, reviewerId);
    }

    // ===================== 조회 =====================

    /**
     * 판매자가 받은 후기 목록. 비회원도 볼 수 있는 판매자 평판이다.
     *
     * <p>정렬은 리포지토리 쿼리에 고정돼 있어 요청 sort를 받지 않는다.
     * 페이지 번호·크기만 쓰도록 Pageable을 다시 만든다 — 그대로 넘기면 Spring이 요청 sort를
     * 쿼리의 ORDER BY 뒤에 덧붙여 실제 순서가 달라진다.
     */
    /**
     * @param viewerId 조회 주체. 비회원 조회에서는 null이다 — 자기 후기의 제목을 가리지 않기 위해 받는다.
     */
    @Transactional(readOnly = true)
    public Slice<UsedReviewResponseDto> getSellerReviews(
            Long sellerId, Long viewerId, Pageable pageable) {
        return usedReviewRepository.findSellerReviews(sellerId, unsorted(pageable))
                .map(review -> UsedReviewResponseDto.from(review, viewerId));
    }

    @Transactional(readOnly = true)
    public Slice<UsedReviewResponseDto> getMyReviews(Long reviewerId, Pageable pageable) {
        // 조회 조건이 작성자로 좁혀져 있어 모든 행의 작성자가 곧 뷰어다.
        return usedReviewRepository.findMyReviews(reviewerId, unsorted(pageable))
                .map(review -> UsedReviewResponseDto.from(review, reviewerId));
    }

    private Pageable unsorted(Pageable pageable) {
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize());
    }

    // ===================== 내부 헬퍼 =====================

    /**
     * 후기 대상 게시글을 잠그고 읽는다.
     *
     * <p><b>게시글의 공개 여부로 거르지 않는다.</b> 후기를 쓸 자격은 "그 거래를 실제로 했는가"
     * (SOLD + 지정 구매자)이지 "게시글이 아직 살아 있는가"가 아니다.
     *
     * <p>삭제된 글을 막으면 판매자가 구매자보다 먼저 글을 지워 나쁜 후기를 원천 봉쇄할 수 있다.
     * 기존 후기를 남기는 이유가 평판 세탁 방지인데, 세탁은 후기가 <b>쓰이기 전</b> 삭제로도 되므로
     * 그 방어가 반쪽이 된다. 숨김·판매자 탈퇴 글에는 작성이 되는데 삭제 글만 막히던 비대칭도 없앤다.
     */
    private UsedProduct getForUpdateOrThrow(Long usedProductId) {
        return usedProductRepository.findByUsedProductIdForUpdate(usedProductId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USED_PRODUCT_NOT_FOUND));
    }

    /**
     * 수정·삭제 대상 조회.
     *
     * <p>조회 조건에 작성자를 함께 넣는다. 후기를 먼저 읽고 소유권을 나중에 비교하면
     * 남의 후기 ID로 존재 여부를 알아낼 수 있다(404와 403이 갈린다).
     * 여기서는 남의 후기든 없는 후기든 같은 404로 응답한다.
     *
     * <p>잠금 순서는 다른 쓰기 경로와 같은 account → used_review다. 계정을 먼저 잠그고
     * 사용 가능 상태를 확인하므로, 탈퇴 정리가 지나간 뒤 살아 있는 토큰으로 후기를 고치는 것도 막힌다.
     */
    private UsedReview getOwnedForUpdateOrThrow(Long reviewerId, Long usedReviewId) {
        accountWriteGuard.lockActive(reviewerId);
        return usedReviewRepository
                .findForUpdateByIdAndReviewer(usedReviewId, reviewerId)
                .orElseThrow(() -> new NotFoundException(ErrorCode.USED_REVIEW_NOT_FOUND));
    }
}

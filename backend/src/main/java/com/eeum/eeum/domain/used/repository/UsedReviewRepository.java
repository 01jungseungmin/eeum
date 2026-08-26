package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedReview;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UsedReviewRepository extends JpaRepository<UsedReview, Long> {

    // 중복 작성 사전 확인. 최종 방어는 uk_used_review_product_reviewer다 —
    // 존재 확인과 INSERT 사이에 다른 요청이 끼어들 수 있어 이 확인만으로는 부족하다.
    boolean existsByUsedProduct_UsedProductIdAndReviewer_AccountId(
            Long usedProductId, Long reviewerAccountId);

    /**
     * 수정·삭제 대상 조회. 소유권까지 한 번에 좁혀 남의 후기 ID로 접근하는 경로를 없앤다.
     *
     * <p>잠금 조회다. 같은 사용자가 수정과 삭제를 동시에 보내면(재시도·두 기기) 일반 조회로는
     * 삭제가 먼저 커밋된 뒤 수정이 flush에서 StaleStateException으로 끝난다 — 사용자에게는 500이다.
     * 잠근 뒤 읽으면 뒤에 온 요청이 사라진 행을 보고 깨끗한 404로 끝난다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
        SELECT r FROM UsedReview r
        WHERE r.usedReviewId = :usedReviewId AND r.reviewer.accountId = :reviewerAccountId
        """)
    Optional<UsedReview> findForUpdateByIdAndReviewer(
            @Param("usedReviewId") Long usedReviewId,
            @Param("reviewerAccountId") Long reviewerAccountId);

    /**
     * 판매자가 받은 후기.
     *
     * <p>후기는 게시글을 거쳐 판매자에 매달리므로 조인이 필요하다.
     * 응답이 게시글 제목·작성자 닉네임을 담고, 공개 여부 판정({@code isPubliclyVisible})이
     * 판매자 상태까지 본다. 셋 다 fetch join해야 한다 — 하나라도 빠지면 페이지 크기만큼
     * 추가 select가 나간다(N+1).
     *
     * <p>정렬은 {@code UsedReviewService}가 고정한 Sort로 들어온다(작성 최신순 + PK tie-break).
     * 여기에 ORDER BY를 함께 두면 Spring이 Pageable의 sort를 그 뒤에 덧붙여 정렬 기준이 둘이 된다.
     * 한 곳에서만 정해야 응답 메타데이터와 실제 SQL이 갈리지 않는다.
     *
     * <p>게시글의 삭제·숨김 여부로 거르지 않는다 — 거르면 판매자가 나쁜 후기가 달린 글을 지워
     * 평판을 세탁할 수 있다. 비공개 게시글의 제목 노출은 응답 단계에서 가린다.
     */
    @Query("""
        SELECT r FROM UsedReview r
        JOIN FETCH r.usedProduct p
        JOIN FETCH p.seller s
        JOIN FETCH r.reviewer
        WHERE s.accountId = :sellerId
        """)
    Slice<UsedReview> findSellerReviews(@Param("sellerId") Long sellerId, Pageable pageable);

    // 내가 쓴 후기. 같은 이유로 fetch join을 쓰고 정렬은 호출부가 고정한다.
    // 여기는 게시글마다 판매자가 다르므로 판매자 fetch join이 특히 중요하다.
    @Query("""
        SELECT r FROM UsedReview r
        JOIN FETCH r.usedProduct p
        JOIN FETCH p.seller
        JOIN FETCH r.reviewer
        WHERE r.reviewer.accountId = :reviewerId
        """)
    Slice<UsedReview> findMyReviews(@Param("reviewerId") Long reviewerId, Pageable pageable);
}

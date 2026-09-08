package com.eeum.eeum.domain.used.repository;

import com.eeum.eeum.domain.used.entity.UsedProduct;
import com.eeum.eeum.domain.used.enums.UsedProductStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface UsedProductRepository
        extends JpaRepository<UsedProduct, Long>, UsedProductRepositoryCustom {

    @EntityGraph(attributePaths = {"seller", "category", "region"})
    Page<UsedProduct> findAllByOrderByCreatedAtDesc(Pageable pageable);

    //  Soft Delete 대상이므로 단건 조회는 항상 이 메서드 사용
    Optional<UsedProduct> findByUsedProductIdAndDeletedAtIsNull(Long usedProductId);

    // 신고 상세의 대상 스냅샷 — 판매자를 함께 조회해 스냅샷 생성 시 추가 SELECT 방지
    @EntityGraph(attributePaths = "seller")
    Optional<UsedProduct> findWithSellerByUsedProductIdAndDeletedAtIsNull(Long usedProductId);

    // 다건 조회 — Soft Delete 대상이므로 삭제된 글을 함께 받으면 안 되는 곳에서 사용한다.
    List<UsedProduct> findByUsedProductIdInAndDeletedAtIsNull(Collection<Long> usedProductIds);

    /**
     * 채팅방 목록의 게시글 요약용 다건 조회 — 판매자를 함께 읽는다.
     *
     * <p>fetch join이 없으면 요약을 만들 때 {@code isPubliclyVisible()}이 판매자 상태를 보면서
     * 게시글 수만큼 select가 더 나간다(N+1).
     *
     * <p>삭제된 글을 걸러내지 않는다. 기존 문의방은 게시글이 사라져도 유지하는 정책이라,
     * 여기서 빼면 "사라진 게시글입니다" 표시 자체가 불가능해진다.
     */
    @Query("SELECT p FROM UsedProduct p JOIN FETCH p.seller WHERE p.usedProductId IN :usedProductIds")
    List<UsedProduct> findAllWithSellerByIdIn(@Param("usedProductIds") Collection<Long> usedProductIds);

    // 판매완료 때 구매자를 생략하면 예약 상대가 그대로 확정된다. 그 상대도 잠그고 검증해야 하므로
    // 잠금 순서를 정하기 위해 미리 읽는다(잠금 없음). 낡은 값은 상품을 잠근 뒤 다시 대조한다.
    @Query("SELECT p.buyer.accountId FROM UsedProduct p WHERE p.usedProductId = :usedProductId")
    Optional<Long> findBuyerIdByUsedProductId(@Param("usedProductId") Long usedProductId);

    // 잠금 순서를 정하려면 판매자 ID가 먼저 필요하다 — 잠그기 전에 스칼라 하나만 읽는다.
    // (p.seller.accountId는 FK 컬럼이라 조인이 없다.) 이 값이 낡아도 안전하다.
    // 잠근 뒤 상품과 판매자 상태를 모두 다시 확인하기 때문이다.
    @Query("SELECT p.seller.accountId FROM UsedProduct p WHERE p.usedProductId = :usedProductId")
    Optional<Long> findSellerIdByUsedProductId(@Param("usedProductId") Long usedProductId);

    // 관리자 조치용 비관적 쓰기 잠금 — 조치와 작성자의 수정·삭제가 동시에 들어오는 경쟁을 막는다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM UsedProduct p WHERE p.usedProductId = :usedProductId")
    Optional<UsedProduct> findByUsedProductIdForUpdate(@Param("usedProductId") Long usedProductId);

    // 찜 카운트 +1 — DB 원자 UPDATE. 읽어서 +1 후 저장하면 동시 찜이 서로의 증가분을 덮어쓴다.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UsedProduct p SET p.favoriteCount = p.favoriteCount + 1 WHERE p.usedProductId = :usedProductId")
    int incrementFavoriteCount(@Param("usedProductId") Long usedProductId);

    // 찜 카운트 -1 — favoriteCount > 0 가드로 음수로 내려가지 않게 한다.
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE UsedProduct p
        SET p.favoriteCount = p.favoriteCount - 1
        WHERE p.usedProductId = :usedProductId
          AND p.favoriteCount > 0
        """)
    int decrementFavoriteCount(@Param("usedProductId") Long usedProductId);

    /**
     * 판매자 제재·탈퇴 시 정리할 예약 건의 ID.
     *
     * <p><b>엔티티가 아니라 ID만 읽는다.</b> 엔티티로 읽으면 영속성 컨텍스트에 올라가고,
     * 뒤이은 {@code findByUsedProductIdForUpdate}가 FOR UPDATE 락은 잡아도
     * 1차 캐시의 낡은 인스턴스를 돌려준다 — 잠근 뒤 상태를 다시 확인한다는 보증이 깨진다.
     *
     * <p>상대(buyer) 지정 여부로 거르지 않는다. 상대 없는 "예약중"도 SELLING으로 되돌려야
     * 판매자 복귀 시 정상 상태가 된다 — 통보만 상대가 있을 때 한다(호출부에서 거른다).
     *
     * <p>ID 오름차순으로 읽어 잠금 순서를 하나로 고정한다.
     */
    @Query("""
        SELECT p.usedProductId FROM UsedProduct p
        WHERE p.seller.accountId = :sellerId
          AND p.status = :status
          AND p.deletedAt IS NULL
        ORDER BY p.usedProductId ASC
        """)
    List<Long> findReservedProductIdsBySeller(
            @Param("sellerId") Long sellerId, @Param("status") UsedProductStatus status);

    // 신고 조치 폴백용 판매자 ID 조회 — 잠금 없이 읽는다.
    // 여기서 상품 행을 잠그면 뒤이어 계정을 잠그게 되어 used_product → account 순서가 되는데,
    // 판매자 경로(AccountWriteGuard)는 account → used_product라 정반대다. 두 요청이 겹치면 교착이다.
    // 이 경로는 상품을 수정하지 않고 조치 대상(계정)만 찾으므로 잠글 이유가 없다.
    @Query("""
        SELECT p.seller.accountId FROM UsedProduct p
        WHERE p.usedProductId = :usedProductId AND p.deletedAt IS NULL
        """)
    Optional<Long> findSellerAccountIdByUsedProductId(@Param("usedProductId") Long usedProductId);

    // 찜 카운트 일괄 -1 — 회원 탈퇴처럼 한 사람의 찜을 한꺼번에 정리할 때 사용.
    // 같은 회원이 같은 글을 두 번 찜할 수 없어(UNIQUE) ID가 중복되지 않으므로 단건 -1의 반복과 결과가 같다.
    @Modifying(clearAutomatically = true)
    @Query("""
        UPDATE UsedProduct p
        SET p.favoriteCount = p.favoriteCount - 1
        WHERE p.usedProductId IN :usedProductIds
          AND p.favoriteCount > 0
        """)
    int decrementFavoriteCounts(@Param("usedProductIds") Collection<Long> usedProductIds);

    // 대상 삭제 시 찜 카운트 0으로 전이 — 찜 행을 지우면 카운트도 함께 0이어야 한다.
    // 남겨두면 삭제된 글이 통계·재계산 기준과 어긋난 값을 갖는다.
    @Modifying(clearAutomatically = true)
    @Query("UPDATE UsedProduct p SET p.favoriteCount = 0 WHERE p.usedProductId = :usedProductId")
    int resetFavoriteCount(@Param("usedProductId") Long usedProductId);

    // 정합성 재계산 — favorite 테이블 실제 row 수로 모든 게시글의 favoriteCount 일괄 갱신.
    // 단일 UPDATE ... SELECT로 처리해 N번 쿼리 없이 처리한다.
    // 삭제된 글도 대상에 포함한다 — 글이 삭제되면 찜도 함께 지워지므로 0이 정답이다.
    @Modifying(clearAutomatically = true)
    @Query(value = """
        UPDATE used_product p
        SET p.favorite_count = (
            SELECT COUNT(*) FROM favorite f
            WHERE f.ref_type = 'USED_PRODUCT' AND f.ref_id = p.used_product_id
        )
        """, nativeQuery = true)
    int recalculateAllFavoriteCounts();
}

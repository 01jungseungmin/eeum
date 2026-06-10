package com.eeum.eeum.domain.favorite.repository;

import com.eeum.eeum.domain.favorite.entity.Favorite;
import com.eeum.eeum.domain.favorite.enums.FavoriteRefType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FavoriteRepository
        extends JpaRepository<Favorite, Long>, FavoriteRepositoryCustom {

    // 본인 찜 단건 조회 (소유권 검증용)
    Optional<Favorite> findByFavoriteIdAndAccount_AccountId(Long favoriteId, Long accountId);

    // 토글 시 기존 찜 조회
    Optional<Favorite> findByAccount_AccountIdAndRefTypeAndRefId(
            Long accountId, FavoriteRefType refType, Long refId);

    // 찜 여부 확인
    boolean existsByAccount_AccountIdAndRefTypeAndRefId(
            Long accountId, FavoriteRefType refType, Long refId);

    // 내 찜 전체 목록 (refType 무관, 최신순)
    Page<Favorite> findByAccount_AccountIdOrderByCreatedAtDesc(Long accountId, Pageable pageable);

    // 타입별 내 찜 목록 (최신순)
    Page<Favorite> findByAccount_AccountIdAndRefTypeOrderByCreatedAtDesc(
            Long accountId, FavoriteRefType refType, Pageable pageable);

    // 대상별 찜 수 (상세 화면용)
    long countByRefTypeAndRefId(FavoriteRefType refType, Long refId);

    // 토글 해제 — 단건 삭제
    void deleteByAccount_AccountIdAndRefTypeAndRefId(
            Long accountId, FavoriteRefType refType, Long refId);

    // 대상 도메인 삭제 시 CASCADE 찜 일괄 삭제 단일 DELETE 쿼리 실행 (파생 쿼리의 N+1 DELETE 방지)
    @Modifying
    @Query("DELETE FROM Favorite f WHERE f.refType = :refType AND f.refId = :refId")
    void deleteAllByRefTypeAndRefId(
            @Param("refType") FavoriteRefType refType, @Param("refId") Long refId);

    // 회원 탈퇴 시 CASCADE 찜 일괄 삭제 단일 DELETE 쿼리 실행 (파생 쿼리의 N+1 DELETE 방지)
    @Modifying
    @Query("DELETE FROM Favorite f WHERE f.account.accountId = :accountId")
    void deleteAllByAccount_AccountId(@Param("accountId") Long accountId);

    // 회원 탈퇴 시 favoriteCount 동기화를 위한 타입별 찜 목록 조회
    List<Favorite> findByAccount_AccountIdAndRefType(Long accountId, FavoriteRefType refType);

    //사장용 찜 고객 목록 — Account JOIN FETCH로 N+1 방지 (@ManyToOne 페치이므로 Pagination과 병용해도 안전)
    @Query(value = """
                SELECT f FROM Favorite f
                JOIN FETCH f.account
                WHERE f.refType = :refType AND f.refId = :refId
                ORDER BY f.createdAt DESC
            """,
           countQuery = """
                SELECT COUNT(f) FROM Favorite f
                WHERE f.refType = :refType AND f.refId = :refId
            """)
    Page<Favorite> findByRefTypeAndRefIdWithAccount(
            @Param("refType") FavoriteRefType refType,
            @Param("refId") Long refId,
            Pageable pageable
    );
}